package dev.tacuchi.hope.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HopeDao {

    /** De la más reciente a la más antigua. */
    @Query("SELECT * FROM hopes ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<Hope>>

    @Query("SELECT * FROM hopes WHERE id = :id")
    suspend fun get(id: Long): Hope?

    @Insert
    suspend fun insert(hope: Hope): Long

    @Update
    suspend fun update(hope: Hope)

    @Query("DELETE FROM hopes WHERE id = :id")
    suspend fun delete(id: Long)

    /** Cada par asset–hope en uso, con el título de la hope. */
    @Query(
        """
        SELECT assetId, id AS hopeId, title FROM (
            SELECT coverAssetId AS assetId, id, title, createdAt FROM hopes WHERE coverAssetId IS NOT NULL
            UNION
            SELECT backgroundAssetId, id, title, createdAt FROM hopes WHERE backgroundAssetId IS NOT NULL
            UNION
            SELECT voiceAssetId, id, title, createdAt FROM hopes WHERE voiceAssetId IS NOT NULL
        ) ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeUses(): Flow<List<AssetUse>>

    @Query(
        """
        SELECT id AS hopeId, :assetId AS assetId, title FROM hopes
        WHERE coverAssetId = :assetId OR backgroundAssetId = :assetId OR voiceAssetId = :assetId
        ORDER BY createdAt DESC, id DESC
        """,
    )
    suspend fun usesOf(assetId: Long): List<AssetUse>
}
