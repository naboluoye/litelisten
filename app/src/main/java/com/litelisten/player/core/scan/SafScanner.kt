package com.litelisten.player.core.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
    suspend fun scan(treeUri: Uri, onProgress: (Int) -> Unit): List<TrackEntity> {
        val sourceId = "saf:" + sha1(treeUri.toString())
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = ArrayList<TrackEntity>()
        walk(root, sourceId, 0, result, onProgress)
        onProgress(result.size)
        return result
    }

    private suspend fun walk(
        dir: DocumentFile,
        sourceId: String,
        depth: Int,
        out: MutableList<TrackEntity>,
        onProgress: (Int) -> Unit,
    ) {
        if (depth > 12) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (name.startsWith(".") || name.equals("Android", true)) continue
                walk(child, sourceId, depth + 1, out, onProgress)
                yield()
            } else if (MimeTypes.isSupported(name)) {
                val uri = child.uri.toString()
                val meta = readMetadata(uri, name)
                val entity = TrackEntity(
                    id = "saf:" + sha1(uri),
                    sourceId = sourceId,
                    sourceType = SourceType.SAF.name,
                    uri = uri,
                    path = name,
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
            .replace(Regex("^\\d{1,3}[\\s._-]+"), "").ifBlank { fileName }
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
}
