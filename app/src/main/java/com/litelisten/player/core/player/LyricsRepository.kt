package com.litelisten.player.core.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌词加载：优先自动找同名 .lrc；Android 10+ 分区存储下非媒体文件不一定可见，
 * 因此始终提供手动选择入口。手动挂载的歌词按曲目 id 存在内存，切换歌曲互不影响。
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _manual = MutableStateFlow<Map<String, List<LyricLine>>>(emptyMap())
    val manual: StateFlow<Map<String, List<LyricLine>>> = _manual.asStateFlow()

    fun setManual(trackId: String, lines: List<LyricLine>) {
        _manual.value = _manual.value + (trackId to lines)
    }

    fun clearManual(trackId: String) {
        _manual.value = _manual.value - trackId
    }

    suspend fun loadAuto(track: Track?): List<LyricLine> = withContext(Dispatchers.IO) {
        if (track == null) return@withContext emptyList()
        readAutoText(track)?.let { LrcParser.parse(it) } ?: emptyList()
    }

    fun readFromUri(uri: Uri): List<LyricLine> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
        LrcParser.parse(text)
    }.getOrDefault(emptyList())

    private fun readAutoText(track: Track): String? {
        val baseName = track.title.substringBeforeLast('.', track.title)
        val lrcName = "$baseName.lrc"
        directSibling(track, lrcName)?.let { return it }
        if (track.sourceType == SourceType.LOCAL || track.sourceType == SourceType.SAF) {
            mediaStoreSibling(track, lrcName)?.let { return it }
        }
        return null
    }

    /** 有真实绝对路径时直接找同目录同名文件；读不到就返回 null，不抛异常。 */
    private fun directSibling(track: Track, lrcName: String): String? {
        val path = track.path
        if (path.isBlank()) return null
        val candidates = ArrayList<File>()
        if (path.startsWith("/")) {
            candidates.add(File(path).parentFile?.let { File(it, lrcName) } ?: return null)
        } else {
            candidates.add(
                File(Environment.getExternalStorageDirectory(), "${path.trimEnd('/')}/$lrcName"),
            )
            // MediaStore 的 RELATIVE_PATH 常包含首级目录，补一次常见的 Music 前缀猜测。
            candidates.add(File(Environment.getExternalStorageDirectory(), "Music/${path.trimEnd('/')}/$lrcName"))
        }
        return candidates.firstNotNullOfOrNull { file ->
            runCatching { if (file.isFile && file.canRead()) file.readText() else null }.getOrNull()
        }?.takeIf { it.isNotBlank() }
    }

    /** 通过 MediaStore.Files 找同名 .lrc；系统不返回非媒体文件时自动降级到手动选择。 */
    private fun mediaStoreSibling(track: Track, lrcName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val audioUri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val folder = context.contentResolver.query(audioUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            if (index < 0) return null
            cursor.getString(index).orEmpty()
        } ?: return null
        if (folder.isBlank()) return null
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val found = context.contentResolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            arrayOf(folder, lrcName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            } else null
        } ?: return null
        return runCatching {
            context.contentResolver.openInputStream(
                ContentUris.withAppendedId(files, found),
            )?.use { it.bufferedReader(Charsets.UTF_8).readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
