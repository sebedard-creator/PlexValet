package com.seb.sdpr.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies")
    suspend fun getAllMoviesOnce(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE ratingKey = :key LIMIT 1")
    suspend fun getByKey(key: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(movies: List<MovieEntity>) {
        deleteAll()
        insertAll(movies)
    }

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getCount(): Int
}
