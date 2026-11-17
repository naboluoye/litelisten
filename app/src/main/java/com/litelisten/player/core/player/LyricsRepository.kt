package com.litelisten.player.core.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.litelisten.player.core.database.LyricsDao
import com.litelisten.player.core.database.ManualLyricsEntity
import com.litelisten.player.core.database.TrackDao
import com.litelisten.player.core.database.toModel
import com.litelisten.player.core.metadata.TagParseUtil
import com.litelisten.player.core.model.SourceType
import com.litelisten.player.core.model.Track
import com.litelisten.player.core.source.smb.SmbConnectionPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** 歌词来源，用于在界面上如实告诉用户这词是从哪儿来的。 */
enum class LyricsSource { MANUAL, SIDE_CAR, SHARED_SIDE_CAR, EMBEDDED, NONE }

data class LoadedLyrics(
    val lines: List<LyricLine> = emptyList(),
    val source: LyricsSource = LyricsSource.NONE,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        val EMPTY = LoadedLyrics()
    }
}

/** 批量导入结果：匹配上的曲目数与扫描到的歌词文件数。 */
data class LyricsImportResult(val scanned: Int, val matched: Int)

/**
 * 歌词加载优先级（自动模式）：
 * 1. 用户手动挂载 / 批量导入（存 manual_lyrics 表，重启不丢）
 * 2. 与歌曲同目录、同名的 .lrc（本地 / SAF / SMB 共享）
 * 3. 扫描时从文件标签提取的内嵌歌词（需带时间戳）
 *
 * 全程离线：不请求任何公网地址，也不上传歌名。
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lyricsDao: LyricsDao,
    private val trackDao: TrackDao,
    private val smb: SmbConnectionPool,
) {
    private val _manual = MutableStateFlow<Map<String, List<LyricLine>>>(emptyMap())
    val manual: StateFlow<Map<String, List<LyricLine>>> = _manual.asStateFlow()

    /** 本地歌词全文搜索：按输入的一句词找出曲库里哪些歌存了含这句的歌词。纯离线。 */
    suspend fun searchLocal(query: String): List<Track> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val ids = runCatching { lyricsDao.searchByContent(q) }.getOrDefault(emptyList())
        ids.chunked(400)
            .flatMap { part -> trackDao.byIds(part) }
            .filter { it.isAvailable }
            .map { it.toModel() }
    }

    /** SMB 命中的歌词按曲目缓存（进歌词页会反复读），未命中同样记住，避免每首都去探一次。 */
    private val smbHits: MutableMap<String, String> =
        Collections.synchronizedMap(LinkedHashMap<String, String>())
    private val smbMisses: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    suspend fun loadAuto(track: Track?): LoadedLyrics = withContext(Dispatchers.IO) {
        if (track == null) return@withContext LoadedLyrics.EMPTY

        val manualText = runCatching { lyricsDao.manualByTrackId(track.id) }.getOrNull()?.content
        if (!manualText.isNullOrBlank()) {
            val lines = LrcParser.parse(manualText)
            if (lines.isNotEmpty()) {
                _manual.value = _manual.value + (track.id to lines)
                return@withContext LoadedLyrics(lines, LyricsSource.MANUAL)
            }
        }

        readSideCar(track)?.let { (text, shared) ->
            val lines = LrcParser.parse(text)
            if (lines.isNotEmpty()) {
                return@withContext LoadedLyrics(
                    lines,
                    if (shared) LyricsSource.SHARED_SIDE_CAR else LyricsSource.SIDE_CAR,
                )
            }
        }

        val embedded = runCatching { lyricsDao.byTrackId(track.id) }.getOrNull()?.content
        if (embedded != null && TagParseUtil.looksSynced(embedded)) {
            val lines = LrcParser.parse(embedded)
            if (lines.isNotEmpty()) return@withContext LoadedLyrics(lines, LyricsSource.EMBEDDED)
        }
        LoadedLyrics.EMPTY
    }

    /** 手动挂载单个 .lrc：读文本 → 落库 → 立即回填内存缓存。 */
    suspend fun attachFile(trackId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { decode(it) }
        if (text.isNullOrBlank()) return@withContext false
        val lines = LrcParser.parse(text)
        if (lines.isEmpty()) return@withContext false
        runCatching {
            lyricsDao.upsertManual(
                listOf(ManualLyricsEntity(trackId, text, System.currentTimeMillis())),
            )
        }
        _manual.value = _manual.value + (trackId to lines)
        smbMisses.remove(trackId)
        true
    }

    /** 直接粘贴网页上复制的歌词文本（支持带时间轴的 LRC）。 */
    suspend fun attachText(trackId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext false
        val lines = LrcParser.parse(cleaned)
        if (lines.isEmpty()) return@withContext false
        runCatching {
            lyricsDao.upsertManual(
                listOf(ManualLyricsEntity(trackId, cleaned, System.currentTimeMillis())),
            )
        }
        _manual.value = _manual.value + (trackId to lines)
        true
    }

    suspend fun removeManual(trackId: String) = withContext(Dispatchers.IO) {
        runCatching { lyricsDao.deleteManual(trackId) }
        _manual.value = _manual.value - trackId
        smbHits.remove(trackId)
        smbMisses.remove(trackId)
    }

    /**
     * 批量导入：选一个文件夹，把里面所有 .lrc 按文件名匹配到曲库。
     * 只接受带时间轴的歌词，纯文本会被跳过（无法跟唱）。
     */
    suspend fun importFolder(treeUri: Uri, onProgress: (Int) -> Unit = {}): LyricsImportResult =
        withContext(Dispatchers.IO) {
            val keys = runCatching { trackDao.lyricKeys() }.getOrDefault(emptyList())
            if (keys.isEmpty()) return@withContext LyricsImportResult(0, 0)
            // 每首歌生成多级候选名（真实文件名 / path 末段 / 标题），统一归一化后建索引；
            // 旧实现只索引 title，标题与文件名不一致（如带歌手前缀）时必然匹配失败。
            data class Indexed(val id: String, val candidates: List<String>)
            val indexed = keys.map { key ->
                val display = audioDisplayName(key.uri)
                val leaf = key.path.substringAfterLast('/')
                Indexed(
                    key.id,
                    LyricsNaming.songCandidates(
                        display ?: "",
                        if (leaf.contains('.')) leaf else "",
                        key.title,
                    ),
                )
            }
            val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
                ?: return@withContext LyricsImportResult(0, 0)
            val files = ArrayList<DocumentFile>()
            collectLyricFiles(root, files, 0)

            var matched = 0
            val batch = ArrayList<ManualLyricsEntity>()
            val now = System.currentTimeMillis()
            files.forEachIndexed { index, doc ->
                val name = doc.name.orEmpty()
                if (!name.endsWith(".lrc", ignoreCase = true)) return@forEachIndexed
                val lyricBase = name.substringBeforeLast('.')
                val hitIds = indexed.filter { LyricsNaming.matches(lyricBase, it.candidates) }
                    .map { it.id }
                if (hitIds.isEmpty()) return@forEachIndexed
                val text = readDocument(doc.uri) ?: return@forEachIndexed
                if (!TagParseUtil.looksSynced(text)) return@forEachIndexed
                if (LrcParser.parse(text).isEmpty()) return@forEachIndexed
                hitIds.forEach { id -> batch.add(ManualLyricsEntity(id, text, now)) }
                matched += hitIds.size
                if (batch.size >= 40) {
                    runCatching { lyricsDao.upsertManual(batch) }
                    batch.clear()
                }
                onProgress(index + 1)
            }
            if (batch.isNotEmpty()) runCatching { lyricsDao.upsertManual(batch) }
            _manual.value = emptyMap()
            LyricsImportResult(files.size, matched)
        }

    fun readFromUri(uri: Uri): List<LyricLine> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching emptyList()
        LrcParser.parse(decode(bytes))
    }.getOrDefault(emptyList())

    // ---- 同目录 .lrc ----

    private fun readSideCar(track: Track): Pair<String, Boolean>? {
        readLocalSideCar(track)?.let { return it to false }
        readSmbSideCar(track)?.let { return it to true }
        return null
    }

    private fun readLocalSideCar(track: Track): String? {
        // 候选歌词名：真实磁盘文件名优先，path 末段其次，title 兜底。
        // 旧实现只用 title，标题与文件名不一致时（如"晴天" 对应 "周杰伦 - 晴天.flac"）必然匹配失败。
        val candidates = LinkedHashSet<String>()
        audioDisplayName(track.uri)?.let { candidates.add(baseNameOf(it) + ".lrc") }
        val leaf = track.path.substringAfterLast('/')
        if (leaf.contains('.')) candidates.add(baseNameOf(leaf) + ".lrc")
        candidates.add(baseNameOf(track.title) + ".lrc")

        for (name in candidates) {
            if (name.isBlank() || name == ".lrc") continue
            directSibling(track, name)?.let { return it }
            if (track.sourceType == SourceType.LOCAL || track.sourceType == SourceType.SAF) {
                mediaStoreSibling(track, name)?.let { return it }
            }
        }
        return null
    }

    /**
     * 为系统文件选择器提供初始目录：把歌曲所在的 RELATIVE_PATH（如 Music/周杰伦/）
     * 转成 externalstorage documents provider 的 tree uri，让用户打开选择器时
     * 直接落在歌曲所在文件夹，而不是每次都从「最近」开始、找不到同目录歌词。
     * 仅对本机 MediaStore 歌曲有效；网络来源或解析失败时返回 null（退回默认目录）。
     */
    fun initialFolderUri(track: Track): Uri? {
        if (track.sourceType != SourceType.LOCAL) return null
        val audioUri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
        val relative = context.contentResolver.query(
            audioUri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                if (index >= 0) cursor.getString(index) else null
            }
        }?.takeIf { it.isNotBlank() }
            ?: return null
        val folderId = relative.trimEnd('/')
        if (folderId.isEmpty()) return null
        // ACTION_OPEN_DOCUMENT 的 EXTRA_INITIAL_URI 要的是 document uri（非 tree uri）。
        // document id 中的 / 与中文都需编码，否则部分 ROM 会认为 URI 非法。
        val encodedId = Uri.encode("primary:$folderId")
        return Uri.parse(
            "content://com.android.externalstorage.documents/document/$encodedId",
        )
    }

    /** MediaStore 音频条目的真实文件名（DISPLAY_NAME），用于推导同名 .lrc。 */
    private fun audioDisplayName(audioUri: String): String? {
        val uri = runCatching { Uri.parse(audioUri) }.getOrNull() ?: return null
        return context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
    }

    /** 有真实绝对路径时直接找同目录同名文件；读不到就返回 null，不抛异常。 */
    private fun directSibling(track: Track, lrcName: String): String? {
        val path = track.path
        if (path.isBlank()) return null
        val file = if (path.startsWith("/")) {
            File(File(path).parentFile ?: return null, lrcName)
        } else {
            // SAF 的 path 末段是文件名而非目录，不能拿外部存储根目录去拼；
            // MediaStore Q+ 的 RELATIVE_PATH 才是相对外部存储的目录（如 Music/周杰伦/）。
            if (track.sourceType == SourceType.SAF) return null
            val relative = path.trimEnd('/')
            if (relative.isEmpty()) return null
            File(Environment.getExternalStorageDirectory(), "$relative/$lrcName")
        }
        return runCatching { if (file.isFile && file.canRead()) decode(file.readBytes()) else null }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 通过 MediaStore.Files 找同名 .lrc。
     * 旧实现要求 RELATIVE_PATH 与歌曲完全相等且 DISPLAY_NAME 完全一致，条件太苛刻：
     * 部分 ROM 不返回 RELATIVE_PATH、歌词名带序号或歌手前缀时就永远找不到。
     * 现在按文件名变体匹配、同目录优先，仍失败再降级到手动选择。
     */
    private fun mediaStoreSibling(track: Track, lrcName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val audioUri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val (folder, _) = context.contentResolver.query(audioUri, projection, null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    "" to ""
                } else {
                    val fi = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    val di = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    (if (fi >= 0) cursor.getString(fi).orEmpty() else "") to
                        (if (di >= 0) cursor.getString(di).orEmpty() else "")
                }
            } ?: ("" to "")

        val lyricBase = LyricsNaming.baseName(lrcName)
        // 用 .lrc 结尾先粗筛，再在 Kotlin 侧用变体匹配，避免 SQL LIKE 转义中文/标点的坑。
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val candidates = runCatching {
            context.contentResolver.query(
                files,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                ),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.lrc"),
                null,
            )?.use { cursor ->
                val out = ArrayList<Pair<Long, Boolean>>() // id to sameFolder
                val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val relCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                if (idCol < 0 || nameCol < 0) return@use out
                var scanned = 0
                while (cursor.moveToNext() && scanned < MEDIA_LRC_SCAN_CAP) {
                    scanned++
                    val display = cursor.getString(nameCol).orEmpty()
                    if (!LyricsNaming.matches(
                            LyricsNaming.baseName(display),
                            listOf(lyricBase),
                        )
                    ) continue
                    val rel = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                    val same = folder.isNotBlank() && rel.equals(folder, ignoreCase = true)
                    out.add(cursor.getLong(idCol) to same)
                }
                out
            }
        }.getOrDefault(emptyList()) ?: emptyList()
        if (candidates.isEmpty()) return null
        // 同目录优先，其次任意一个同名 .lrc。
        val chosen = candidates.firstOrNull { it.second }?.first ?: candidates.first().first
        return runCatching {
            context.contentResolver.openInputStream(ContentUris.withAppendedId(files, chosen))
                ?.use { decode(it.readBytes()) }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * SMB 共享目录里的同名 .lrc。网络共享上的歌原本完全拿不到歌词，
     * 这里按曲目相对路径推导出 .lrc 路径直接读，不额外申请权限。
     */
    private fun readSmbSideCar(track: Track): String? {
        if (track.sourceType != SourceType.SMB) return null
        smbHits[track.id]?.let { return it }
        if (smbMisses.contains(track.id)) return null
        val lrcPath = LyricsNaming.sideCarPath(track.path) ?: return null
        val text = runCatching {
            val opened = smb.openFile(track.sourceId, lrcPath)
            try {
                val size = opened.size
                if (size <= 0L || size > MAX_LYRICS_BYTES) return@runCatching null
                val buffer = ByteArray(size.toInt())
                var read = 0
                while (read < buffer.size) {
                    val count = opened.handle.read(buffer, read.toLong(), read, buffer.size - read)
                    if (count <= 0) break
                    read += count
                }
                if (read <= 0) null else decode(buffer.copyOf(read))
            } finally {
                runCatching { opened.handle.close() }
            }
        }.getOrNull()
        if (text.isNullOrBlank() || !text.contains('[')) {
            smbMisses.add(track.id)
            return null
        }
        if (smbHits.size >= SMB_CACHE_SIZE) smbHits.clear()
        smbHits[track.id] = text
        return text
    }

    private fun baseNameOf(title: String): String = title.substringBeforeLast('.', title)

    // ---- 文本读取 ----

    private fun readDocument(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_LYRICS_BYTES) null else decode(bytes)
        }
    }.getOrNull()

    private fun collectLyricFiles(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > MAX_DIR_DEPTH || out.size >= MAX_IMPORT_FILES) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        children.forEach { child ->
            if (out.size >= MAX_IMPORT_FILES) return
            if (child.isDirectory) {
                collectLyricFiles(child, out, depth + 1)
            } else if (child.name?.endsWith(".lrc", ignoreCase = true) == true) {
                out.add(child)
            }
        }
    }

    /**
     * 歌词文件编码很杂：UTF-8、带 BOM 的 UTF-8、GBK 都有。
     * 先按严格 UTF-8 解，抛错再退回 GBK，避免 NAS 上的中文歌词全是乱码。
     */
    private fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val body = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        }.getOrElse {
            runCatching { String(body, charset("GBK")) }.getOrDefault("")
        }
    }

    private companion object {
        const val MAX_LYRICS_BYTES = 1_000_000L
        const val MAX_IMPORT_FILES = 20_000
        const val MAX_DIR_DEPTH = 8
        const val SMB_CACHE_SIZE = 32
        const val MEDIA_LRC_SCAN_CAP = 5000
    }
}
