package com.litelisten.player.core.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.litelisten.player.core.common.DurationFilter
import com.litelisten.player.core.common.MimeTypes
import com.litelisten.player.core.common.Pinyin
import com.litelisten.player.core.database.TrackEntity
import com.litelisten.player.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.yield
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** SAF 目录递归；只索引音频，不修改、不删除用户文件。 */
@Singleton
class SafScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 同一个目录树必须复用同一个 sourceId，空目录时也要能定位到旧记录。 */
    fun sourceId(treeUri: Uri): String = "saf:" + sha1(treeUri.toString())

    suspend fun scan(
        treeUri: Uri,
        onProgress: (Int) -> Unit,
        minMs: Long = 0L,
        maxMs: Long = 0L,
    ): List<TrackEntity> {
        val sourceId = sourceId(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = ArrayList<TrackEntity>()
        walk(root, sourceId, "", 0, result, onProgress, minMs, maxMs)
        onProgress(result.size)
        return result
    }

    private suspend fun walk(
        dir: DocumentFile,
        sourceId: String,
        relativeDir: String,
        depth: Int,
        out: MutableList<TrackEntity>,
        onProgress: (Int) -> Unit,
        minMs: Long,
        maxMs: Long,
    ) {
        if (depth > 12) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            val name = child.name ?: continue
            val relativePath = if (relativeDir.isEmpty()) name else "$relativeDir/$name"
            if (child.isDirectory) {
                if (name.startsWith(".") || name.equals("Android", true)) continue
                walk(child, sourceId, relativePath, depth + 1, out, onProgress, minMs, maxMs)
                yield()
            } else if (MimeTypes.isSupported(name)) {
                val uri = child.uri.toString()
                val meta = readMetadata(uri, name)
                if (!DurationFilter.keep(meta.durationMs, minMs, maxMs)) continue
                val entity = TrackEntity(
                    id = "saf:" + sha1(uri),
                    sourceId = sourceId,
                    sourceType = SourceType.SAF.name,
                    uri = uri,
                    // 存相对所选文件夹的路径，文件夹分类与同名歌词推导都依赖它。
                    path = relativePath,
                    title = meta.title,
                    titleNorm = Pinyin.normalize(meta.title),
                    artist = meta.artist,
                    artistNorm = meta.artist?.let { Pinyin.normalize(it) },
                    album = meta.album,
                    albumNorm = meta.album?.let { Pinyin.normalize(it) },
                    albumArtist = meta.artist,
                    trackNo = null,
                    discNo = null,
                    durationMs = meta.durationMs,
                    year = null,
                    genre = null,
                    mimeType = MimeTypes.fromPath(name),
                    bitrateKbps = null,
                    sampleRateHz = null,
                    channels = null,
                    bitDepth = null,
                    sizeBytes = child.length().takeIf { it > 0 },
                    lastModified = child.lastModified(),
                    artworkKey = null,
                    addedAt = System.currentTimeMillis(),
                )
                out.add(entity)
                if (out.size % 100 == 0) onProgress(out.size)
            }
        }
    }

    private data class Meta(val title: String, val artist: String?, val album: String?, val durationMs: Long)

    private fun readMetadata(uri: String, fileName: String): Meta {
        val fallbackTitle = fileName.substringBeforeLast('.', fileName)
            .replace(TRACK_NO_PREFIX, "").ifBlank { fileName }
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, Uri.parse(uri))
            Meta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() },
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        }.getOrElse {
            Meta(fallbackTitle, null, null, 0L)
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** 去掉文件名开头的曲目序号（如 "03 晴天" → "晴天"）。 */
        val TRACK_NO_PREFIX = Regex("^\\d{1,3}[\\s._-]+")
    }
}
