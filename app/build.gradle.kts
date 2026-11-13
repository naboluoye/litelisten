import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// 版本唯一来源：根目录 version.properties。CI 可用 -PciVersionName / -PciVersionCode 覆盖。
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.isFile) FileInputStream(f).use { load(it) }
}
val appVersionCode = (findProperty("ciVersionCode") as? String)?.toIntOrNull()
    ?: versionProps.getProperty("versionCode", "1").toInt()
val appVersionName = (findProperty("ciVersionName") as? String)?.takeIf { it.isNotBlank() }
    ?: versionProps.getProperty("versionName", "1.0.0")

// 签名只从环境变量(CI)或 gitignore 的 keystore.properties(本地)读取，仓库内绝无密钥明文。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) FileInputStream(f).use { load(it) }
}
fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val resolvedStoreFile = signingValue("LITELISTEN_KEYSTORE_PATH", "storeFile")
val resolvedStorePassword = signingValue("LITELISTEN_KEYSTORE_PASSWORD", "storePassword")
val resolvedKeyAlias = signingValue("LITELISTEN_KEY_ALIAS", "keyAlias")
val resolvedKeyPassword = signingValue("LITELISTEN_KEY_PASSWORD", "keyPassword")
    ?: resolvedStorePassword
val hasReleaseSigning = listOf(
    resolvedStoreFile, resolvedStorePassword, resolvedKeyAlias, resolvedKeyPassword
).all { !it.isNullOrBlank() }

// 发布流水线强制验签：缺密钥直接失败，绝不产出 debug 签名或 unsigned 的 Release 包。
if (System.getenv("LITELISTEN_REQUIRE_SIGNING") == "true" && !hasReleaseSigning) {
    throw GradleException(
        "Release 签名配置缺失：设置 LITELISTEN_KEYSTORE_PATH / LITELISTEN_KEYSTORE_PASSWORD / " +
            "LITELISTEN_KEY_ALIAS / LITELISTEN_KEY_PASSWORD，或在本地提供 keystore.properties"
    )
}

android {
    namespace = "com.litelisten.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.litelisten.player"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(resolvedStoreFile!!)
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // smbj / bcprov / jmdns 的 META-INF 冲突预留（P3/P4 接入时无需再动 Gradle）。
    packaging {
        resources {
            excludes += setOf("META-INF/*", "META-INF/versions/**", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // 版本被《04 架构》冻结，"有新版本可用"属于预期噪音；升级只在版本目录统一评估。
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.google.material)
    implementation(libs.core.splashscreen)
    implementation(libs.palette)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)
    implementation(libs.timber)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // P3/P4/P6 才会真正调用，P0 先锁定版本确保解析通过。
    implementation(libs.smbj)
    implementation(libs.jmdns)
    implementation(libs.tinypinyin)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
