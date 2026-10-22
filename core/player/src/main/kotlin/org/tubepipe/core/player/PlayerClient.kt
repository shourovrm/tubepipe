package org.tubepipe.core.player

/** The YouTube player clients the extractor can ask for, named as PipePipe's `setYoutubePlayerClient` expects. */
enum class PlayerClient(val extractorName: String) {
    /** Serves HLS; what PipePipe uses when logged out. */
    VisionOs("visionos"),

    /** Serves SABR; the fallback when VisionOS fails. */
    Mweb("mweb"),
    ;

    companion object {
        val DEFAULT = VisionOs

        /** The preferred client first, then every other client, so a failed extraction can be retried. */
        fun fallbackOrder(preferred: PlayerClient): List<PlayerClient> =
            listOf(preferred) + entries.filter { client -> client != preferred }
    }
}
