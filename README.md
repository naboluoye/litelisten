# 轻听 LiteListen · Android 局域网音乐播放器

> **当前版本：v2.0.0 完整可用版** ｜ 仓库：https://github.com/naboluoye/litelisten
> [v2.0.0 Release 下载页](https://github.com/naboluoye/litelisten/releases/tag/v2.0.0) ｜ [v1.0.0（历史骨架版）](https://github.com/naboluoye/litelisten/releases/tag/v1.0.0)
> 包名 `com.litelisten.player` ｜ minSdk 26（Android 8.0）/ targetSdk 35 ｜ Kotlin 2.1.0 + Compose + Media3 + Room
> 零账号、零广告、零遥测、零外网请求：所有网络目标在请求发出前强制校验为局域网私网地址。

**v2.0.0 与 v1.0.0 的区别**：v1.0.0 只是 P0 工程骨架，界面提示「P1 / P3 / P4 开发中」，扫描与添加服务器都不可用；**v2.0.0 把 P1–P7 全部实现为真实功能**，是可以直接当播放器使用的产品。两个版本使用同一把签名密钥，**已装 v1.0.0 可直接覆盖升级，数据不丢**。

---

## 扫码下载（v2.0.0）

![轻听 v2.0.0 扫码下载](docs/qr-download.png)

- **扫码直达 APK**：<https://github.com/naboluoye/litelisten/releases/download/v2.0.0/LiteListen-v2.0.0.apk>
- 版本说明与 SHA-256：[GitHub Release v2.0.0](https://github.com/naboluoye/litelisten/releases/tag/v2.0.0)
- 可转发的下载卡片：[docs/qr-download-card.png](docs/qr-download-card.png)
- 下载后在系统里允许「安装未知来源」即可安装。二维码采用 30% 纠错等级并保留静默区，缩放或打印后仍可识别（已用解码器实测校验）。

---

## 1. 现在能做什么

| 模块 | 实际功能 |
|---|---|
| 本机曲库 | 首次进入申请读取音频权限；扫描 MediaStore 全部音乐，过滤系统音/0 字节/短音频（可开关）；按**歌曲 / 专辑 / 艺术家 / 文件夹**四视图浏览，支持排序、升降序、按来源筛选 |
| 目录扫描 | 「＋」选一个文件夹，通过 SAF 递归索引（不修改、不删除用户文件）；曲库页常驻「重新扫描」按钮，随时可重扫 |
| 播放 | Media3 `MediaSessionService` 后台播放，通知栏 / 锁屏 / 蓝牙按键控制；队列拖拽与删除、随机、单曲/列表循环、0.5–2× 倍速、10 秒快进退、睡眠定时（最后 10 秒淡出）、杀进程后恢复队列与进度 |
| 网络源 | **SMB**（smbj 随机读 DataSource，支持拖动进度）、**WebDAV**（PROPFIND 目录浏览 + OkHttp Range 流播）、**DLNA/UPnP**（SSDP 发现 + ContentDirectory Browse）；均可测试连接、浏览目录、直接播放、整目录导入曲库 |
| 凭据 | 用户名/密码用 Android Keystore AES-256-GCM 加密后存储，明文不落盘、不进 URI、不进日志；地址不合规直接拒绝连接 |
| 搜索 | 中文原名 / 全拼 / 首字母混合索引，空格分词 AND 语义；同时搜歌曲、专辑、艺术家；保留最近 10 条搜索历史 |
| 其它 | 收藏、最近播放、LRC 歌词（自动找同名文件 + 手动选择 .lrc，点击歌词行跳转）、均衡器与低音增强（挂到真实 audioSessionId）、首次使用 3 页引导、浅色/深色/纯黑主题、动态取色 |
| 设置 | 主题、动态取色、断点恢复、自动播放、拔耳机暂停、淡出、出错自动跳过、倍速、扫描短音频、仅 Wi-Fi、连接超时、缓冲时长、缓存上限、重建索引、清理封面缓存 |

所有设置项都接了真实逻辑：超时/缓冲/缓存/仅 Wi-Fi/拔耳机暂停/淡出经 `RuntimeConfig` 快照驱动 OkHttp、ExoPlayer、拦截器与封面缓存；下次新建连接或播放器时生效，不打断正在播放的音频。

---

## 2. 上手三步（用户视角）

1. **装好打开**：首次启动是 3 页引导，完成后按提示授予「读取音频」权限，曲库会自动可扫。
2. **扫描本机音乐**：曲库页右上角的 ⟳ 就是重新扫描；空曲库时也有「扫描本机音乐」按钮。想只加某个目录，点 ＋ 选择文件夹。
3. **加网络服务器**：底部「来源」→ 右上角 ＋ → 选「添加 SMB 服务器」或「添加 WebDAV 服务器」，填地址后点「测试连接」再「保存」；DLNA 设备点「扫描局域网设备」自动发现。保存后点「浏览」即可进目录播放，或「加入曲库」整目录导入。

> 服务器地址请填**局域网 IP**（如 `192.168.1.10`）或 `.local` 名称；公网地址会被直接拦截，这是刻意设计，不是故障。SMB 匿名访问请勾「匿名登录」。

---

## 3. 已真实跑通的验证（v2.0.0）

| 项目 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ **6 个测试类 / 20 条用例，0 失败 0 错误** |
| `./gradlew :app:lintDebug` | ✅ 0 error（仅有 2 条已审计的库注入/导出服务告警） |
| `./gradlew :app:assembleRelease`（R8 混淆 + 资源压缩 + 强制签名） | ✅ BUILD SUCCESSFUL，产物 **8.6 MB**（≤ 20 MB 门禁） |
| `apksigner verify --verbose` | ✅ v2 + v3 签名有效，RSA 4096，证书 SHA-256 `cc7b03e7…e7ac`（与 v1.0.0 一致，可覆盖升级） |
| `ci_guard.py --check all` | ✅ PASS，且契约模型的中文兜底告警已清零 |
| `ci_guard.py --check apk` | ✅ PASS，合并后 Manifest 权限 = 11 项主动申请 + 2 项已审计库注入 |
| GitHub Actions `CI` / `Release` | 见仓库 Actions；tag `v2.0.0` 触发签名发版 |

本机验证使用 JDK 21 + 临时安装的 Android 35 SDK；云端流水线使用 Temurin 17 与 GitHub Secrets 中的正式密钥。

**能力边界（如实说明）**：本机没有可连接的 Android 真机，无法代替你完成真机安装、蓝牙/锁屏控制、不同 ROM 的 MediaStore 兼容性、以及 `16_验收清单与测试矩阵.md` 里的人工勾选项；这些仍需在设备上逐条验证。

---

## 4. 环境与本地构建

| 项 | 版本 |
|---|---|
| JDK | **17**（CI 用 Temurin 17；本机 JDK 25 不兼容，JDK 21 可用） |
| Android SDK | Platform 35 + Build Tools 35.0.0 |
| Kotlin / AGP / Gradle | 2.1.0 / 8.7.3 / 8.9（全部锁在 `gradle/libs.versions.toml`） |

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest :app:lintDebug
python3 scripts/ci_guard.py --check all
```

Windows 用 `gradlew.bat`。本地要出签名 Release 包，可复制 `keystore.properties.example` 为 `keystore.properties` 填真实值后跑 `./gradlew :app:assembleRelease`（该文件已 gitignore）；也可设置 `LITELISTEN_*` 环境变量（见 `app/build.gradle.kts`）。

---

## 5. CI/CD 上线路径

```
push / PR ──> ci.yml ──> 红线守卫 → assembleDebug → 单测 → Lint → 上传产物
改 version.properties → commit → git tag v2.0.0 → push tag
        ▼
release.yml ──> 版本一致性校验 → 红线守卫 → 单测门禁
             → 从 Secrets 解码 keystore → assembleRelease（强制验签）
             → apksigner verify → 包体 ≤ 20MB → aapt badging → 产物权限白名单审计
             → 重命名 + SHA-256 → 上传 Artifact（含 R8 mapping）→ 发布 GitHub Release
```

分发目标是 **GitHub Release 自分发 APK**，不接 Google Play / 国内商店。

### 已完成的一次性配置

- GitHub 账号 `naboluoye`，公开仓库 `litelisten`，默认分支 `main`。
- 正式密钥 `litelisten.jks`（RSA 4096 / 10000 天 / 别名 `litelisten`）放在**工程目录之外**，未进 Git；口令由用户离线保管。
- 4 个 Secrets：`ANDROID_KEYSTORE_BASE64`、`LITELISTEN_KEYSTORE_PASSWORD`、`LITELISTEN_KEY_ALIAS`、`LITELISTEN_KEY_PASSWORD`。
- 密钥丢失 = **已装用户无法覆盖升级**，请务必离线备份。

### 云端流水线踩坑记录（已修复，勿回退）

| 现象 | 原因 | 修法 |
|---|---|---|
| `setup-android@v3` 20 秒失败：`Failed to find package 'tools'` | 该 action 会装 Google 已下架的旧 SDK 包 | 两个 workflow 改用 runner 预装 SDK，显式设 `ANDROID_HOME` 并动态定位 `sdkmanager` |
| `./gradlew: Permission denied` | Windows 提交丢了可执行位 | `git update-index --chmod=+x gradlew` + `.gitattributes` 固定 `gradlew text eol=lf` |

### 每次发版 SOP

```bash
python3 scripts/bump_version.py 2.0.1     # versionCode 自动 +1
git add version.properties && git commit -m "release: 2.0.1"
git tag -a v2.0.1 -m "Release 2.0.1"
git push origin main --tags               # 推 tag 后 release.yml 自动跑
```

tag 与 `version.properties` 不一致会被硬门禁拒绝。也可在 Actions 页手动触发 `Release` 并填 `version`。

### 红线守卫 `scripts/ci_guard.py`

| 检查 | 拦什么 |
|---|---|
| `permissions` | Manifest 权限必须与 11 项白名单完全一致 |
| `dependencies` | 禁止 firebase / crashlytics / 广告 / 统计 / GMS / GPL 依赖 |
| `lan` | `LanOnlyInterceptor` 必须存在且挂在所有 OkHttp 出口 |
| `source` | 禁止 `TODO()` / `NotImplementedError(` / `GlobalScope` / `Thread.sleep` / `println` / `Log.d`；禁止源码硬编码中文 UI 文案 |
| `build` / `version` / `secrets` | 锁定构建参数与版本一致性；禁止密钥入库 |
| `apk` | 对打包产物跑 `aapt dump badging`，白名单外新增权限直接失败 |

---

## 6. 隐私与红线

- 纯本地 / 局域网：`LanOnlyInterceptor` 校验 RFC1918 / loopback / link-local / ULA / `.local`，公网地址连请求都不发出。
- 零账号、零广告、零遥测、零崩溃上报；日志只在 DEBUG 构建输出，Release 不种树。
- 不删除、不修改用户音乐文件；「移除曲库」只删索引记录。
- 不申请白名单外权限；合并后 APK 多出的 `RECEIVE_BOOT_COMPLETED`（WorkManager）与 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（androidx.core）已审计并加入白名单。
- 所有用户可见文字集中在 `res/values/strings.xml`；凭据用 Android Keystore 加密，无明文密码。

---

## 7. 与文档规格的差异（有依据的主动修正）

| # | 文档写的 | 实际做法 | 原因 |
|---|---|---|---|
| 1 | 不含 Material Components | 加 `material:1.12.0` | Compose Material3 AAR 不含 `Theme.Material3.DayNight.NoActionBar` XML 父样式 |
| 2 | `tinypinyin 2.0.3` | 额外加镜像仓库并 `content { includeGroup }` 限制 | 该版本不在 Maven Central / JitPack |
| 3 | 未指定 slf4j-nop | 锁 `2.0.9` | 与 smbj 0.14.0 的 `slf4j-api` 对齐 |
| 4 | P0 主题默认 DARK | 默认 SYSTEM | 《05 契约》§10 规定 |
| 5 | 抛 `NotImplementedError` 的空实现 | 只冻结接口，P1–P7 全部落真实实现 | 空实现是运行期地雷 |
| 6 | `LibraryUiState.Content` 含 `LazyPagingItems` | 放在 `ui/library/` 按签名建 | Compose 类型不该进纯 Kotlin 的 `core/model` |
| 7 | P7 才处理混淆 | P0 预置 R8 规则并真跑 | smbj 传递依赖引用 Android 不存在的 `javax.el.*`，已定向 `-dontwarn` |
| 8 | MediaStore `IS_MUSIC != 0` 过滤 | 读出来后判定，不用 SQL 硬过滤 | 部分 ROM 未打标记会整库漏歌；`IS_MUSIC` 是 API 29 才加入，放进投影会让 Android 8/9 抛无效列 |
| 9 | 权限严格 11 项 | 源 Manifest 11 项；合并后 +2 项库注入 | 删除会破坏旧系统动态接收器保护与 WorkManager 重启 |
| 10 | 歌词只读同名文件 | 同名自动查找 + 手动选择 .lrc | Android 10+ 分区存储下非媒体文件常不可见，必须有可靠兜底 |
| 11 | 均衡器挂全局混音 session 0 | 生成并发布真实 `audioSessionId` 后挂接 | 挂 session 0 在很多机型上对播放无效 |

---

## 8. GitHub 开源参考（只取思路，未复制代码）

| 项目 | License | 借鉴内容 |
|---|---|---|
| [Yasinjahantigh/LogaRhythm-Android](https://github.com/Yasinjahantigh/LogaRhythm-Android) | Apache-2.0 | env 驱动签名、`apksigner verify`、tag → GitHub Release 全链路 |
| [District1655/WebDavMusicPlayer](https://github.com/District1655/WebDavMusicPlayer) | 未声明 | `OkHttpDataSource` + `callTimeout(0)` 防长音频被掐断 |
| [PixelPlayerHQ/PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) | **GPL-3.0** | 仅看 CI 布局，未复制代码、未引入依赖 |
| [cwuom/NeriPlayer](https://github.com/cwuom/NeriPlayer) | **GPL-3.0** | 仅看模块化拆分思路，同上 |

所有依赖仍严格为 Apache-2.0 / MIT / BSD，未引入 GPL 组件。

---

## 9. 目录速览

```
litelisten/
├─ .github/workflows/{ci.yml, release.yml}     CI 门禁 / 签名发版
├─ .gitattributes                              gradlew 可执行位与 LF 换行
├─ docs/{qr-download.png, qr-download-card.png}  v2.0.0 扫码下载图
├─ scripts/{ci_guard.py, bump_version.py}      红线守卫 / 版本自增
├─ gradle/libs.versions.toml                   唯一依赖版本源
├─ version.properties                          versionCode=2 / versionName=2.0.0
├─ app/proguard-rules.pro                      R8 / kotlinx.serialization 规则
└─ app/src/main/java/com/litelisten/player/
   ├─ core/{model, common, designsystem, database, datastore, network, scan, player}
   ├─ core/source/{api, smb, webdav, dlna}     三类网络源 + 凭据加密
   ├─ data/repository/                          Room / 搜索 / 来源实现
   ├─ di/                                      6 个 Hilt Module
   └─ ui/{navigation, library, browse, search, player, settings}
```

---

## 10. 已知限制

- **未在真机安装验证**：本机无可用 Android 设备，冷启动耗时、大字体 1.3×、旋转/分屏、锁屏与蓝牙控制、不同 ROM 的 MediaStore 差异仍需按 `16_验收清单与测试矩阵.md` 人工勾验。
- `LanAddress` 按字符串判断私网，不做 DNS 解析：SMB / WebDAV 主机请填 **IP** 或 `.local` 名称。
- DLNA 能否播放取决于媒体服务器返回的 `res` 资源 URL 是否可被本机直连（部分设备需额外头，已注入 `transferMode.dlna.org` / `getcontentFeatures.dlna.org`）。
- 歌词自动匹配在 Android 13+ 常受分区存储限制，此时请用「选择 .lrc 歌词文件」手动挂载；手动挂载为会话内有效。
- 均衡器效果依赖设备/system 的 `android.media.audiofx` 实现，部分蓝牙设备或 ROM 可能不支持（页面会提示）。
- `connectedDebugAndroidTest` 仪器化测试与真机全流程回归矩阵仍未接入 CI。
