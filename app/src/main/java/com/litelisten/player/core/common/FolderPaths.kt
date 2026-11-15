package com.litelisten.player.core.common

/**
 * 各来源的 [com.litelisten.player.core.model.Track.path] 形态并不一致，直接取首段会把
 * 本机音乐全部归到 `storage` 一个文件夹里。这里统一归一化成「相对存储根的目录」：
 *
 * - MediaStore 绝对路径 `/storage/emulated/0/Music/周杰伦/七里香.flac` → `Music/周杰伦`
 * - MediaStore（Android 10+）的 RELATIVE_PATH `Music/周杰伦/`（本身是目录）
 * - SAF 相对根路径 `sub/七里香.flac` → `sub`
 * - SMB 相对路径 `Music/七里香.flac` → `Music`
 * - 只有文件名、没有任何目录信息时返回空串，表示「根目录」
 */
object FolderPaths {
    private val AUDIO_EXTENSION = Regex(
        "\\.(mp3|flac|m4a|aac|ogg|oga|opus|wav|wave|wma|ape|alac|dsf|dff|aif|aiff|mp4|m4b|mka|webm|amr|mid|midi)$",
        RegexOption.IGNORE_CASE,
    )

    /** 抹掉 `/storage/emulated/0/`、`/storage/<卷>/`、`/sdcard/`、`/mnt/...` 这类挂载前缀。 */
    private val VOLUME_PREFIX = Regex(
        "^/(storage/(emulated/\\d+|[^/]+)|sdcard|mnt/sdcard|mnt/media_rw/[^/]+)/",
        RegexOption.IGNORE_CASE,
    )

    fun folderOf(path: String): String {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isEmpty()) return ""
        val stripped = VOLUME_PREFIX.replace(normalized, "").trim('/')
        if (stripped.isEmpty()) return ""
        val name = stripped.substringAfterLast('/')
        val isDirectory = normalized.endsWith("/") || name.isEmpty() || !AUDIO_EXTENSION.containsMatchIn(name)
        val folder = if (isDirectory) stripped else stripped.substringBeforeLast('/', "")
        return folder.trim('/')
    }

    /** 判断某首歌是否属于 [folder]（含子目录）；folder 为空表示根目录。 */
    fun isUnder(folder: String, path: String): Boolean {
        val target = folder.trim('/')
        val actual = folderOf(path)
        if (target.isEmpty()) return actual.isEmpty()
        return actual == target || actual.startsWith("$target/")
    }
}
