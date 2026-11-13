# ---------------- P7 R8 规则（P0 预置，避免后续为混淆返工） ----------------
# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-dontwarn dagger.hilt.**

# smbj / BouncyCastle（P3）
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
# smbj -> mbassador 事件总线引用了 Java EE 的 javax.el.*（Android 不存在且运行期不会走到），
# R8 严格模式会把它当 missing class 直接失败；只压制这一类，不关闭混淆。
-dontwarn javax.el.**
-dontwarn net.engio.mbassy.dispatch.el.**

# jmdns（P4）
-keep class javax.jmdns.** { *; }
-keep class net.sf.jmDNS.** { *; }
-dontwarn javax.jmdns.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 曲库实体、来源配置和队列快照在 Release 包里要能被 Room / 序列化反射找到。
-keep,includedescriptorclasses class com.litelisten.player.core.model.** { *; }
-keep,includedescriptorclasses class com.litelisten.player.core.database.** { *; }
-keep,includedescriptorclasses class com.litelisten.player.core.datastore.QueueSnapshot { *; }
-keep class com.litelisten.player.core.model.**$$serializer { *; }
-keep class com.litelisten.player.core.datastore.**$$serializer { *; }

# 反射 / 枚举
-keepclassmembers enum * { *; }

# 崩溃定位（日志只落本地文件，不上传）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
