package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.remote.MoviesContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexMoviePageValidatorTest {
    private val gson = Gson()

    private fun page(json: String): MoviesContainer =
        gson.fromJson(json, MoviesContainer::class.java)

    private fun assertIncomplete(block: () -> Unit) {
        val error = assertThrows(IllegalStateException::class.java) { block() }
        assertTrue(error.message.orEmpty().contains("Bibliothèque locale conservée"))
    }

    @Test
    fun `short page does not end sync before the announced total`() {
        val validator = PlexMoviePageValidator("Films")
        validator.validate(page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        assertEquals(1, validator.nextStart)
        assertFalse(validator.isComplete)

        validator.validate(page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"2","title":"Film B"}]}"""))
        assertEquals(2, validator.nextStart)
        assertTrue(validator.isComplete)
    }

    @Test
    fun `later pages may omit the total once it is known`() {
        val validator = PlexMoviePageValidator("Films")
        validator.validate(page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        validator.validate(page("""{"size":1,"Metadata":[{"ratingKey":"2","title":"Film B"}]}"""))
        assertTrue(validator.isComplete)
    }

    @Test
    fun `explicitly empty libraries are valid without metadata`() {
        for (json in listOf("""{"size":0}""", """{"totalSize":0}""", """{"size":0,"totalSize":0,"Metadata":[]}""")) {
            val validator = PlexMoviePageValidator("Films")
            assertTrue(validator.validate(page(json)).isEmpty())
            assertTrue(validator.isComplete)
        }
    }

    @Test
    fun `missing container is rejected`() {
        assertIncomplete { PlexMoviePageValidator("Films").validate(null) }
    }

    @Test
    fun `absent total is not replaced by the size of a nonempty page`() {
        assertIncomplete {
            PlexMoviePageValidator("Films").validate(page("""{"size":1,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        }
    }

    @Test
    fun `missing metadata with outstanding movies is rejected`() {
        assertIncomplete { PlexMoviePageValidator("Films").validate(page("""{"size":0,"totalSize":2}""")) }
    }

    @Test
    fun `premature empty page is rejected`() {
        val validator = PlexMoviePageValidator("Films")
        validator.validate(page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        assertIncomplete { validator.validate(page("""{"size":0,"totalSize":2,"Metadata":[]}""")) }
    }

    @Test
    fun `movies missing an identifier or title are rejected`() {
        for (movie in listOf("""{"title":"Film A"}""", """{"ratingKey":" " ,"title":"Film A"}""", """{"ratingKey":"1"}""", """{"ratingKey":"1","title":" "}""")) {
            assertIncomplete {
                PlexMoviePageValidator("Films").validate(page("""{"size":1,"totalSize":1,"Metadata":[$movie]}"""))
            }
        }
    }

    @Test
    fun `repeated page is rejected even when its count reaches the total`() {
        val validator = PlexMoviePageValidator("Films")
        val repeated = page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}""")
        validator.validate(repeated)
        assertIncomplete { validator.validate(repeated) }
        assertFalse(validator.isComplete)
    }

    @Test
    fun `duplicate identifiers within a page are rejected`() {
        assertIncomplete {
            PlexMoviePageValidator("Films").validate(page("""{"size":2,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"},{"ratingKey":"1","title":"Film A"}]}"""))
        }
    }

    @Test
    fun `changed total is rejected`() {
        val validator = PlexMoviePageValidator("Films")
        validator.validate(page("""{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        assertIncomplete {
            validator.validate(page("""{"size":1,"totalSize":3,"Metadata":[{"ratingKey":"2","title":"Film B"}]}"""))
        }
    }

    @Test
    fun `announced page size must match its contents`() {
        assertIncomplete {
            PlexMoviePageValidator("Films").validate(page("""{"size":2,"totalSize":2,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        }
    }

    @Test
    fun `page exceeding the total is rejected`() {
        assertIncomplete {
            PlexMoviePageValidator("Films").validate(page("""{"size":1,"totalSize":0,"Metadata":[{"ratingKey":"1","title":"Film A"}]}"""))
        }
    }
}
