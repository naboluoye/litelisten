package com.litelisten.player

import android.app.Application
import com.litelisten.player.data.repository.SourceRepositoryImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LiteListenApp : Application() {
    @Inject lateinit var sourceRepository: SourceRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        // Release 不种树：日志既不落 logcat，也不上传（项目零遥测）。
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // 已保存的网络源在杀进程后需要重新注册到内存（连接池 / 认证头），
        // 放到后台线程，不阻塞首帧。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { sourceRepository.hydrate() }
        }
    }
}
