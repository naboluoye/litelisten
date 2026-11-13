package com.litelisten.player.core.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.common.Pinyin
import com.litelisten.player.core.database.TrackEntity
import com.litelisten.player.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedTrack(
    val entity: TrackEntity,
    val albumId: Long,
    val contentUri: String,
)

/** MediaStore 只做批量读取与过滤；封面、标签补齐交给独立低优先级队列。 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scan(showShortAudio: Boolean, onProgress: (Int) -> Unit): List<ScannedTrack> {
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        if (hasRelativePath) projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        else projection.add(MediaStore.Audio.Media.DATA)

        // 不用 SQL 层 IS_MUSIC 硬过滤：部分 ROM 未给文件打标记会整库漏歌，
        // 且 IS_MUSIC 常量是 API 29 才加入，放进投影会让 Android 8/9 直接抛无效列。
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: return emptyList()

        val result = ArrayList<ScannedTrack>(cursor.count.coerceAtLeast(0))
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathCol = c.getColumnIndexOrThrow(
                if (hasRelativePath) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
            )

            var batch = 0
            while (c.moveToNext()) {
                val duration = c.getLong(durationCol)
                val size = c.getLong(sizeCol)
                val path = c.getString(pathCol).orEmpty()
                if (size <= 0L) continue
                if (!showShortAudio && duration in 1..29_999L) continue
                if (isSystemSound(path)) continue

                val mediaId = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
                ).toString()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: path.substringAfterLast('/').substringBeforeLast('.', path)
                        .ifBlank { "Track $mediaId" }
                val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != UNKNOWN }
                val album = c.getString(albumCol)?.takeIf { it.isNotBlank() }
                val albumId = c.getLong(albumIdCol)
                val addedAt = c.getLong(addedCol) * 1000L
                val entity = TrackEntity(
                    id = "local:$mediaId",
                    sourceId = "local",
                    sourceType = SourceType.LOCAL.name,
                    uri = uri,
                    path = path,
                    title = title,
                    titleNorm = Pinyin.normalize(title),
                    artist = artist,
                    artistNorm = artist?.let { Pinyin.normalize(it) },
                    album = album,
                    albumNorm = album?.let { Pinyin.normalize(it) },
                    albumArtist = artist,
                    trackNo = c.getInt(trackCol).takeIf { it > 0 },
                    discNo = null,
                    durationMs = duration,
                    year = c.getInt(yearCol).takeIf { it > 1900 && it < 2200 },
                    genre = null,
                    mimeType = c.getString(mimeCol) ?: MimeTypes.fromPath(path),
                    bitrateKbps = null,
                    sampleRateHz = null,
                    channels = null,
                    bitDepth = null,
                    sizeBytes = size,
                    lastModified = c.getLong(modifiedCol) * 1000L,
                    artworkKey = null,
                    addedAt = if (addedAt > 0) addedAt else System.currentTimeMillis(),
                )
                result.add(ScannedTrack(entity, albumId, uri))
                batch++
                if (batch % 200 == 0) {
                    onProgress(result.size)
                    yield()
                }
            }
            onProgress(result.size)
        }
        return result
    }

    private fun isSystemSound(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("ringtones/") || lower.contains("alarms/") ||
            lower.contains("notifications/") || lower.contains("/android/data/")
    }

    private companion object {
        const val UNKNOWN = "<unknown>"
    }
}
