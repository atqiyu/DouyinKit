package com.atqiyu.douyinkit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AwemeEntity::class,
        DownloadHistoryEntity::class,
        DownloadCollectionEntity::class,
        DownloadCollectionMemberEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun awemeDao(): AwemeDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao

    abstract fun downloadCollectionDao(): DownloadCollectionDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `download_collections` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `download_collection_members` (" +
                        "`awemeId` TEXT NOT NULL, `collectionId` TEXT NOT NULL, " +
                        "`assignedAt` INTEGER NOT NULL, PRIMARY KEY(`awemeId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_download_collection_members_collectionId` " +
                        "ON `download_collection_members` (`collectionId`)",
                )
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "douyinkit.db",
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
