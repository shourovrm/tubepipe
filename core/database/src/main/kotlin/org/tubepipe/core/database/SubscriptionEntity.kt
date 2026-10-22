package org.tubepipe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A subscribed channel. The channel url is the key because it is what NewPipe and PipePipe exports identify it by. */
@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey val url: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAtMillis: Long,
)
