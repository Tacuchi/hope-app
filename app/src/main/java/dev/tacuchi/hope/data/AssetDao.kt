package dev.tacuchi.hope.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    /** Del más reciente al más antiguo; [type] nulo trae todos. */
    @Query("SELECT * FROM assets WHERE (:type IS NULL OR type = :type) ORDER BY createdAt DESC, id DESC")
    fun observe(type: AssetType?): Flow<List<Asset>>

    @Query("SELECT MAX(number) FROM assets WHERE nameKind = :kind")
    suspend fun maxNumber(kind: AssetNameKind): Int?

    @Insert
    suspend fun insert(asset: Asset): Long

    @Delete
    suspend fun delete(asset: Asset)
}
