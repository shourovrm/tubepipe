package org.tubepipe.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscription WHERE url = :url")
    suspend fun delete(url: String)

    @Query("SELECT * FROM subscription ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscription WHERE url = :url)")
    fun observeIsSubscribed(url: String): Flow<Boolean>

    @Query("SELECT url FROM subscription")
    suspend fun allUrls(): List<String>
}
