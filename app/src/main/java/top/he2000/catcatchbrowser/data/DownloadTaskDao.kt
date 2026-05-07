package top.he2000.catcatchbrowser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = :status")
    suspend fun getByStatus(status: String): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity): Long

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Delete
    suspend fun delete(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_tasks WHERE status IN ('completed', 'failed', 'cancelled')")
    suspend fun deleteFinished()

    @Query("UPDATE download_tasks SET status = :status, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET progress = :progress, downloadedSegments = :downloaded, totalSegments = :total, currentSpeed = :speed, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, downloaded: Int, total: Int, speed: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun updateTaskProgress(id: Long, progress: Float, downloaded: Int, total: Int, speed: String) {
        updateProgress(id, progress, downloaded, total, speed)
    }
}

@Database(entities = [DownloadTaskEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}
