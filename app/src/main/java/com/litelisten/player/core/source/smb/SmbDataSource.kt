@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.litelisten.player.core.source.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException
import java.net.URLDecoder

/** smb:// 随机读：ExoPlayer 拖动进度时按 DataSpec.position 重新 open 即可跳转。 */
class SmbDataSource(
    private val pool: SmbConnectionPool,
) : BaseDataSource(true) {
    private var handle: SmbFile? = null
    private var dataUri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        val sourceId = uri.host ?: throw IOException("MissingSourceId")
        val rawPath = uri.encodedPath?.removePrefix("/").orEmpty()
        val path = URLDecoder.decode(rawPath, "UTF-8")
        val opened = pool.openFile(sourceId, path)
        handle = opened.handle
        dataUri = uri
        position = dataSpec.position
        val size = opened.size
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size > 0L -> (size - dataSpec.position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val file = handle ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length
        else minOf(length.toLong(), bytesRemaining).toInt()
        val read = file.read(buffer, position, offset, toRead)
        if (read < 0) return C.RESULT_END_OF_INPUT
        position += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataUri

    override fun close() {
        val opened = dataUri != null
        dataUri = null
        runCatching { handle?.close() }
        handle = null
        if (opened) transferEnded()
    }
}

class SmbDataSourceFactory(
    private val pool: SmbConnectionPool,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(pool)
}
