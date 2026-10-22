package org.tubepipe.core.player

enum class Delivery { Hls, Dash, Sabr, Progressive }

/** What [TubepipePlayer.load] ended up playing. */
data class LoadedStream(val client: PlayerClient, val delivery: Delivery, val title: String)
