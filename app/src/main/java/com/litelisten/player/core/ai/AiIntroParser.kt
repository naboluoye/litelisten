package com.litelisten.player.core.ai

/**
 * Pure parser: splits the model reply into the three requested sections.
 * Markers are injected (they live in string resources), so this stays testable
 * and free of user-visible literals in code.
 */
object AiIntroParser {

    data class Sections(
        val background: String,
        val artist: String,
        val lyricsIntro: String,
        val raw: String,
    ) {
        val isStructured: Boolean get() = background.isNotBlank() && artist.isNotBlank()
    }

    /**
     * @param reply full model reply text
     * @param backgroundMarker e.g. the bracketed section header for song background
     * @param artistMarker same for artist intro
     * @param lyricsMarker same for lyrics intro
     */
    fun parse(
        reply: String,
        backgroundMarker: String,
        artistMarker: String,
        lyricsMarker: String,
    ): Sections {
        val raw = reply.trim()
        val idxBg = raw.indexOf(backgroundMarker)
        val idxArtist = raw.indexOf(artistMarker)
        val idxLyrics = raw.indexOf(lyricsMarker)
        if (idxBg < 0 || idxArtist < 0 || idxLyrics < 0) {
            return Sections("", "", "", raw)
        }
        fun slice(from: Int, to: Int, marker: String) = raw
            .substring(from, to)
            .removePrefix(marker)
            .trim()
        return Sections(
            background = slice(idxBg, idxArtist, backgroundMarker),
            artist = slice(idxArtist, idxLyrics, artistMarker),
            lyricsIntro = slice(idxLyrics, raw.length, lyricsMarker),
            raw = raw,
        )
    }
}
