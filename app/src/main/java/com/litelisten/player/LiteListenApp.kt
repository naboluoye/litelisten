package com.litelisten.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LiteListenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Release 不种树：日志既不落 logcat，也不上传（项目零遥测）。
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
