package dev.tacuchi.hope.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetType
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    /** Del más reciente al más antiguo; [type] nulo trae todos. */
    @Query("SELECT * FROM assets WHERE (:type IS NULL OR type = :type) ORDER BY createdAt DESC, id DESC")
    fun observe(type: AssetType?): Flow<List<AssetEntity>>

    @Query("SELECT MAX(number) FROM assets WHERE nameKind = :kind")
    suspend fun maxNumber(kind: AssetNameKind): Int?

    @Query("SELECT * FROM assets WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun get(id: Long): AssetEntity?

    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Delete
    suspend fun delete(asset: AssetEntity)
}

@Dao
interface HopeDao {

    /** De la más reciente a la más antigua. */
    @Query("SELECT * FROM hopes ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<HopeEntity>>

    @Query("SELECT * FROM hopes WHERE id = :id")
    suspend fun get(id: Long): HopeEntity?

    @Insert
    suspend fun insert(hope: HopeEntity): Long

    @Update
    suspend fun update(hope: HopeEntity)

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
    fun observeUses(): Flow<List<AssetUseRow>>

    @Query(
        """
        SELECT id AS hopeId, :assetId AS assetId, title FROM hopes
        WHERE coverAssetId = :assetId OR backgroundAssetId = :assetId OR voiceAssetId = :assetId
        ORDER BY createdAt DESC, id DESC
        """,
    )
    suspend fun usesOf(assetId: Long): List<AssetUseRow>
}
