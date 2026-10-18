package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.remote.PlexMovie
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlexMovieDetailsLoaderTest {
    private val gson = Gson()

    private fun movies(count: Int): List<PlexMovie> = (1..count).map { key ->
        gson.fromJson("""{"ratingKey":"$key","title":"Film $key"}""", PlexMovie::class.java)
    }

    @Test
    fun `four requests overlap and a free slot is immediately reused`() = runBlocking {
        withTimeout(10_000) {
            val input = movies(10)
            val gates = input.associate { checkNotNull(it.ratingKey) to CompletableDeferred<Unit>() }
            val started = Channel<String>(Channel.UNLIMITED)
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val result = async {
                PlexMovieDetailsLoader().load(input) { key ->
                    val current = active.incrementAndGet()
                    peak.accumulateAndGet(current, ::maxOf)
                    try {
                        started.send(key)
                        gates.getValue(key).await()
                        input.first { it.ratingKey == key }
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }

            val initialRequests = List(4) { started.receive() }
            assertEquals(4, active.get())
            assertTrue(started.tryReceive().isFailure)
            assertFalse(result.isCompleted)

            // The fifth request must start while three initial requests are still blocked.
            gates.getValue(initialRequests.first()).complete(Unit)
            val nextRequest = started.receive()
            assertFalse(nextRequest in initialRequests)
            assertEquals(4, active.get())

            gates.values.forEach { it.complete(Unit) }
            val loaded = result.await()
            assertEquals(input, loaded.map { it.first })
            assertEquals(input, loaded.map { it.second })
            assertEquals(4, peak.get())
            assertEquals(0, active.get())
        }
    }

    @Test
    fun `completion order does not change movie order or pairings`() = runBlocking {
        withTimeout(10_000) {
            val input = movies(3)
            val gates = input.associate { checkNotNull(it.ratingKey) to CompletableDeferred<Unit>() }
            val started = Channel<String>(Channel.UNLIMITED)
            val completed = Channel<String>(Channel.UNLIMITED)
            val result = async {
                PlexMovieDetailsLoader().load(input) { key ->
                    started.send(key)
                    gates.getValue(key).await()
                    completed.send(key)
                    input.first { it.ratingKey == key }.copy(summary = "Details $key")
                }
            }
            repeat(3) { started.receive() }
            for (key in listOf("3", "2", "1")) {
                gates.getValue(key).complete(Unit)
                assertEquals(key, completed.receive())
            }

            val loaded = result.await()
            assertEquals(input, loaded.map { it.first })
            assertEquals(listOf("Details 1", "Details 2", "Details 3"), loaded.map { it.second?.summary })
        }
    }

    @Test
    fun `failed and missing details keep the original movies`() = runBlocking {
        withTimeout(10_000) {
            val input = movies(8)
            val calls = AtomicInteger()
            val loaded = PlexMovieDetailsLoader().load(input) { key ->
                calls.incrementAndGet()
                when (key) {
                    "2" -> throw IOException("Detail request failed")
                    "3" -> null
                    else -> input.first { it.ratingKey == key }
                }
            }

            assertEquals(input, loaded.map { it.first })
            assertEquals(8, calls.get())
            assertNull(loaded[1].second)
            assertNull(loaded[2].second)
            assertSame(input.last(), loaded.last().second)
        }
    }

    @Test
    fun `cancelling a load cancels active requests and does not start queued requests`() = runBlocking {
        withTimeout(10_000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val calls = AtomicInteger()
            val cancelled = AtomicInteger()
            val loader = PlexMovieDetailsLoader()
            val result = async {
                loader.load(movies(20)) { key ->
                    calls.incrementAndGet()
                    try {
                        started.send(key)
                        awaitCancellation()
                    } finally {
                        cancelled.incrementAndGet()
                    }
                }
            }
            repeat(4) { started.receive() }
            result.cancelAndJoin()

            assertTrue(result.isCancelled)
            assertEquals(4, calls.get())
            assertEquals(4, cancelled.get())

            // A subsequent load must still have access to all permits.
            assertEquals(6, loader.load(movies(6)) { null }.size)
        }
    }

    @Test
    fun `cancellation exceptions are propagated rather than treated as missing details`() = runBlocking {
        withTimeout(10_000) {
            try {
                PlexMovieDetailsLoader().load(movies(1)) {
                    throw CancellationException("Sync cancelled")
                }
                fail("Cancellation must propagate")
            } catch (expected: CancellationException) {
                assertEquals("Sync cancelled", expected.message)
            }
        }
    }

    @Test
    fun `overlapping loads share the same four request limit`() = runBlocking {
        withTimeout(10_000) {
            val loader = PlexMovieDetailsLoader()
            val started = Channel<Unit>(Channel.UNLIMITED)
            val gate = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val loads = List(2) {
                async {
                    loader.load(movies(8)) {
                        val current = active.incrementAndGet()
                        peak.accumulateAndGet(current, ::maxOf)
                        try {
                            started.send(Unit)
                            gate.await()
                            null
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            repeat(4) { started.receive() }
            assertEquals(4, active.get())
            assertTrue(started.tryReceive().isFailure)
            gate.complete(Unit)

            assertEquals(listOf(8, 8), loads.awaitAll().map { it.size })
            assertEquals(4, peak.get())
        }
    }

    @Test
    fun `progress is reported before the whole page completes`() = runBlocking {
        withTimeout(10_000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val progress = Channel<Int>(Channel.UNLIMITED)
            val gates = movies(4).associate { checkNotNull(it.ratingKey) to CompletableDeferred<Unit>() }
            val result = async {
                PlexMovieDetailsLoader().load(movies(4), onProgress = { progress.trySend(it).getOrThrow() }) { key ->
                    started.send(key)
                    gates.getValue(key).await()
                    null
                }
            }
            repeat(4) { started.receive() }
            gates.getValue("3").complete(Unit)
            assertEquals(1, progress.receive())
            assertFalse(result.isCompleted)
            gates.values.forEach { it.complete(Unit) }
            assertEquals(listOf(2, 3, 4), List(3) { progress.receive() })
            result.await()
            Unit
        }
    }

    @Test
    fun `concurrent completions publish ordered counts including failed details`() = runBlocking {
        val progress = mutableListOf<Int>()
        PlexMovieDetailsLoader().load(movies(1000), onProgress = { progress.add(it) }) { key ->
            if (key.toInt() % 3 == 0) throw IOException("Unavailable details")
            null
        }
        assertEquals((1..1000).toList(), progress)
    }

    @Test
    fun `empty page makes no detail request`() = runBlocking {
        val loaded = PlexMovieDetailsLoader().load(emptyList()) {
            error("No request expected")
        }
        assertTrue(loaded.isEmpty())
    }
}
