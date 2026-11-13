@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.litelisten.player.core.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.litelisten.player.core.source.smb.SmbDataSourceFactory
import java.io.IOException

/** 按 scheme 分发：smb:// 走随机读，其它交给 DefaultDataSource（content:// + OkHttp）。 */
class LiteListenDataSourceFactory(
    private val defaultFactory: DataSource.Factory,
    smbPool: com.litelisten.player.core.source.smb.SmbConnectionPool,
) : DataSource.Factory {
    private val smbFactory = SmbDataSourceFactory(smbPool)

    override fun createDataSource(): DataSource =
        DispatchDataSource(defaultFactory.createDataSource(), smbFactory.createDataSource())

    private class DispatchDataSource(
        private val fallback: DataSource,
        private val smb: DataSource,
    ) : DataSource {
        private var active: DataSource? = null

        override fun open(dataSpec: DataSpec): Long {
            val chosen: DataSource = if (dataSpec.uri.scheme.equals("smb", true)) smb else fallback
            active = chosen
            return chosen.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: throw IOException("DataSourceNotOpen")

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun addTransferListener(transferListener: TransferListener) {
            fallback.addTransferListener(transferListener)
            smb.addTransferListener(transferListener)
        }

        override fun close() {
            runCatching { active?.close() }
            active = null
        }
    }
}
