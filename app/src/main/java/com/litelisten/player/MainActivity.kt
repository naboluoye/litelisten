package com.litelisten.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litelisten.player.core.datastore.SettingsKeys
import com.litelisten.player.core.designsystem.theme.LiteListenTheme
import com.litelisten.player.ui.OnboardingScreen
import com.litelisten.player.ui.navigation.LiteListenRoot
import com.litelisten.player.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingDone by settingsViewModel.onboardingResolved.collectAsStateWithLifecycle()
            val prefs by settingsViewModel.prefs.collectAsStateWithLifecycle()
            val dynamicColor = prefs[SettingsKeys.DYNAMIC_COLOR_KEY] ?: false
            // 权限只在首次进入主界面时申请一次，避免每次重组都弹窗。
            var permissionRoundDone by remember { mutableStateOf(false) }

            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {}
            val audioLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                // 授权成功立刻自动扫一次：装完就有歌，不用用户自己去找扫描按钮。
                if (granted) settingsViewModel.autoScanIfEmpty()
                // 两个系统弹窗不能同时发起，音频权限回来后再申请通知权限。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(onboardingDone) {
                if (onboardingDone != true || permissionRoundDone) return@LaunchedEffect
                permissionRoundDone = true
                val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, audioPermission,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    // 已有权限（覆盖升级 / 权限被系统保留）时同样补一次空库扫描。
                    settingsViewModel.autoScanIfEmpty()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    audioLauncher.launch(audioPermission)
                }
            }

            LiteListenTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                when (onboardingDone) {
                    null -> Box(Modifier.fillMaxSize())
                    true -> LiteListenRoot()
                    false -> OnboardingScreen(onDone = { settingsViewModel.setOnboardingDone(true) })
                }
            }
        }
    }
}
