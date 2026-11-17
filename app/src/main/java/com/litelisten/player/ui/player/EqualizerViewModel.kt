package com.litelisten.player.ui.player

import androidx.lifecycle.ViewModel
import com.litelisten.player.core.player.EqualizerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 均衡器页面的轻量代理。真正的音效引擎在 [EqualizerController]（单例）里，
 * 这样页面返回、ViewModel 被销毁时不会释放音效——修复「打开音效返回就自动关闭」。
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: EqualizerController,
) : ViewModel() {
    val sessionId get() = controller.sessionId
    val supported get() = controller.supported
    val bands get() = controller.bands
    val levels get() = controller.levels
    val bandRange get() = controller.bandRange
    val presets get() = controller.presets
    val enabled get() = controller.enabled
    val bass get() = controller.bass

    fun init(session: Int) = controller.init(session)
    fun setEnabled(value: Boolean) = controller.setEnabled(value)
    fun setBand(index: Int, level: Int) = controller.setBand(index, level)
    fun setPreset(index: Int) = controller.setPreset(index)
    fun setBass(strength: Int) = controller.setBass(strength)
}
