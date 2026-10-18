package com.seb.sdpr.domain.usecase

import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.domain.model.LibrarySyncProgress
import javax.inject.Inject

class SyncLibraryUseCase @Inject constructor(
    private val repository: MovieRepository
) {
    suspend operator fun invoke(
        serverIp: String,
        token: String,
        forceFull: Boolean = false,
        onProgress: (LibrarySyncProgress) -> Unit = {}
    ): Result<Int> = repository.syncLibrary(serverIp, token, forceFull, onProgress)
}
