package org.tubepipe.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SearchHistoryEntity::class,
        SavedSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TubepipeDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchDao(): SearchDao

    companion object {
        private const val FILE_NAME = "tubepipe.db"

        fun create(context: Context): TubepipeDatabase =
            Room.databaseBuilder(context.applicationContext, TubepipeDatabase::class.java, FILE_NAME)
                .addCallback(LikedPlaylistCallback)
                .build()

        fun inMemory(context: Context): TubepipeDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, TubepipeDatabase::class.java)
                .addCallback(LikedPlaylistCallback)
                .build()
    }

    /** Every database starts with the Liked playlist, so "like" never has to check whether it exists. */
    private object LikedPlaylistCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO playlist (id, name, createdAtMillis) VALUES " +
                    "(${PlaylistEntity.LIKED_ID}, '${PlaylistEntity.LIKED_NAME}', ${System.currentTimeMillis()})",
            )
        }
    }
}
