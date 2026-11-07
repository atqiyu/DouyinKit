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
        ShareLinkRecordEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun awemeDao(): AwemeDao

    abstract fun downloadHistoryDao(): DownloadHistoryDao

    abstract fun downloadCollectionDao(): DownloadCollectionDao

    abstract fun shareLinkDao(): ShareLinkDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `share_link_records` (" +
                        "`url` TEXT NOT NULL, `awemeId` TEXT, `title` TEXT, " +
                        "`status` TEXT NOT NULL, `message` TEXT, " +
                        "`firstCapturedAt` INTEGER NOT NULL, " +
                        "`lastCapturedAt` INTEGER NOT NULL, " +
                        "`captureCount` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "PRIMARY KEY(`url`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_share_link_records_status` " +
                        "ON `share_link_records` (`status`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_share_link_records_lastCapturedAt` " +
                        "ON `share_link_records` (`lastCapturedAt`)",
                )
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "douyinkit.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
