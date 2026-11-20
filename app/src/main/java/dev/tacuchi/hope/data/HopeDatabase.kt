package dev.tacuchi.hope.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Asset::class], version = 1)
abstract class HopeDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao

    companion object {
        fun create(context: Context): HopeDatabase =
            Room.databaseBuilder(context, HopeDatabase::class.java, "hope.db").build()
    }
}
