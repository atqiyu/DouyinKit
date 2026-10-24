package com.atqiyu.douyinkit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AwemeEntity::class, DownloadHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun awemeDao(): AwemeDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "douyinkit.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
