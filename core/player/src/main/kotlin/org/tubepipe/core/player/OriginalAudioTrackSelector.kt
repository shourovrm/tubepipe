package org.tubepipe.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

/**
 * Switches an HLS stream with several audio languages to its original-language track.
 * Media3 would otherwise pick the track that matches the device language, which on YouTube is often a dub.
 * A track in [preferredLanguage] is chosen instead when there is one.
 * Call [reset] before each new media source so the choice is made again for it.
 */
@OptIn(UnstableApi::class)
class OriginalAudioTrackSelector(private val player: Player, private val preferredLanguage: String? = null) :
    Player.Listener {
    private var hasChosen = false

    fun reset() {
        hasChosen = false
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (hasChosen) {
            return
        }
        val audioTracks = audioTracksOf(tracks)
        if (audioTracks.isEmpty()) {
            return
        }
        hasChosen = true
        if (audioTracks.size == 1) {
            return
        }
        val candidates = audioTracks.map { audioTrack -> audioTrack.toCandidate() }
        val chosen = audioTracks[AudioTrackChoice.pickIndex(candidates, preferredLanguage)]
        if (chosen.isSelected) {
            return
        }
        val override = TrackSelectionOverride(chosen.group.mediaTrackGroup, chosen.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    private class AudioTrack(val group: Tracks.Group, val trackIndex: Int) {
        val isSelected: Boolean get() = group.isTrackSelected(trackIndex)

        fun toCandidate(): AudioCandidate {
            val format = group.getTrackFormat(trackIndex)
            // HLS renditions flag the original track DEFAULT=YES but name it only by language, so the flag
            // is turned into the word the shared choice logic already looks for.
            val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
            val trackName = listOfNotNull(format.label, "default".takeIf { isDefault }).joinToString(" ")
            return AudioCandidate(
                trackId = format.language ?: format.id,
                trackName = trackName.ifEmpty { null },
                bitrate = format.bitrate,
            )
        }
    }

    private fun audioTracksOf(tracks: Tracks): List<AudioTrack> {
        val audioTracks = mutableListOf<AudioTrack>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) {
                continue
            }
            for (trackIndex in 0 until group.length) {
                if (group.isTrackSupported(trackIndex)) {
                    audioTracks.add(AudioTrack(group, trackIndex))
                }
            }
        }
        return audioTracks
    }
}
