package com.litelisten.player.core.scan

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.litelisten.player.core.datastore.RuntimeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** 封面缩到最长边 512 后落 cacheDir；失败不影响播放，UI 用占位图。 */
@Singleton
class ArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: RuntimeConfig,
) {
    private val dir: File by lazy { File(context.cacheDir, "artwork").apply { mkdirs() } }

    fun artworkFile(key: String): File = File(dir, key)

    fun extract(albumId: Long, contentUri: String): String? {
        val name = sha1("album:$albumId") + ".jpg"
        val target = File(dir, name)
        if (target.exists() && target.length() > 0) return name
        val bitmap = loadThumbnail(albumId) ?: loadEmbedded(contentUri) ?: return null
        val scaled = scale(bitmap)
        return runCatching {
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            trimToLimit()
            name
        }.getOrNull()
    }

    /** 按设置里的上限淘汰最旧的封面，不让缓存无限增长。 */
    private fun trimToLimit() {
        val limit = runtime.cacheLimitMb.toLong() * 1024L * 1024L
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        val oldestFirst = files.iterator()
        while (total > limit && oldestFirst.hasNext()) {
            val file = oldestFirst.next()
            total -= file.length()
            runCatching { file.delete() }
        }
    }

    fun clear(): Long {
        var bytes = 0L
        dir.listFiles()?.forEach { bytes += it.length(); it.delete() }
        return bytes
    }

    private fun loadThumbnail(albumId: Long): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || albumId <= 0) return null
        return runCatching {
            val uri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            )
            context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        }.getOrNull()
    }

    private fun loadEmbedded(contentUri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(contentUri))
            val data = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scale(source: Bitmap): Bitmap {
        val max = source.width.coerceAtLeast(source.height)
        if (max <= 512) return source
        val ratio = 512f / max
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
