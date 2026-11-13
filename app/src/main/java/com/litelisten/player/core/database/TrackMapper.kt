package com.litelisten.player.core.database

import com.litelisten.player.core.model.Album
import com.litelisten.player.core.model.Artist
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track

private fun safeType(value: String): SourceType =
    runCatching { SourceType.valueOf(value) }.getOrDefault(SourceType.LOCAL)

fun TrackEntity.toModel(): Track = Track(
    id = id,
    sourceId = sourceId,
    sourceType = safeType(sourceType),
    uri = uri,
    path = path,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    trackNo = trackNo,
    discNo = discNo,
    durationMs = durationMs,
    year = year,
    genre = genre,
    mimeType = mimeType,
    bitrateKbps = bitrateKbps,
    sampleRateHz = sampleRateHz,
    channels = channels,
    bitDepth = bitDepth,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    artworkKey = artworkKey,
    isFavorite = isFavorite,
    isAvailable = isAvailable,
    addedAt = addedAt,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
)

fun AlbumRow.toModel(): Album = Album(
    key = name + "|" + (artist ?: ""),
    name = name,
    artist = artist,
    artworkKey = artworkKey,
    trackCount = trackCount,
    year = year,
)

fun ArtistRow.toModel(): Artist = Artist(
    key = name,
    name = name,
    albumCount = albumCount,
    trackCount = trackCount,
    artworkKey = artworkKey,
)
