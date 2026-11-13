# 轻听 LiteListen · Android 局域网音乐播放器

> 仓库：**https://github.com/naboluoye/litelisten** ｜ 首个正式版：[v1.0.0 Release](https://github.com/naboluoye/litelisten/releases/tag/v1.0.0)
> 当前交付：**M0 / P0 工程骨架** + **完整 CI/CD 上线路径**（云端流水线已实跑通过）。
> 包名 `com.litelisten.player` ｜ minSdk 26 / targetSdk 35 ｜ Kotlin 2.1.0 + Compose + Media3。
> 零账号、零广告、零遥测、零外网请求；网络访问只允许局域网私网地址。

## 0. 已真实跑通的验证结果

交付前在本机临时安装 Android 35 SDK，**实际执行了完整构建**（非静态检查）：

| 项目 | 结果 |
|---|---|
| `./gradlew help` | ✅ Gradle 8.9 配置阶段通过，插件 / Version Catalog 全部解析成功 |
| `./gradlew :app:assembleDebug` | ✅ 成功，0 error、0 Kotlin 警告；产物 31.2 MB（未混淆） |
| `./gradlew :app:testDebugUnitTest` | ✅ **5 个测试类 / 16 条用例，0 失败 0 错误** |
| `./gradlew :app:lintDebug` | ✅ 0 error、仅 1 条 `DataExtractionRules` 提示（`allowBackup` 在 Android 12+ 弃用，但 minSdk 26 仍需它保护旧设备；已叠加 `data_extraction_rules.xml` 零外传） |
| `./gradlew :app:assembleRelease`（R8 + 强制签名） | ✅ 成功，产物 **7.96 MB**（≤ 20 MB 门禁） |
| `apksigner verify --verbose` | ✅ v2 + v3 签名有效 |
| `aapt dump badging` | ✅ versionCode 1 / versionName 1.0.0 / minSdk 26 / targetSdk 35 / 应用名「轻听」 |
| `ci_guard.py --check all` | ✅ PASS（含 1 条已知中文兜底告警） |
| GitHub Actions `CI`（云端） | ✅ **success**，6m19s：红线守卫 → assembleDebug → 单测 → Lint |
| GitHub Actions `Release`（云端） | ✅ **success**，4m48s：强制签名 → 验签 → 包体/权限审计 → 发布 GitHub Release |
| Release APK 下载与验签 | ✅ `LiteListen-v1.0.0.apk`（8,350,386 B）+ SHA-256 校验通过；v2/v3 验签通过 |
| 签名证书一致性 | ✅ APK 证书 SHA-256 `cc7b03e7…e7ac` 与正式 `litelisten.jks` 完全一致 |

本机表格里的构建验证用的是临时 SDK（JDK 21）与**一次性测试密钥**；云端 Release 用的是已配置好的**正式密钥**（见 §3）。`app/build/outputs/.../*.apk` 是本机验证产物，**不要分发**；正式安装包请从 GitHub Release 下载。

---

## 1. 这次交付了什么

| 层 | 内容 |
|---|---|
| 工程 | Gradle 8.9 Kotlin DSL + Version Catalog，wrapper 齐全（`gradlew` / `gradlew.bat` / jar），开箱可 `./gradlew` |
| 契约 | 《05 契约》全部领域模型、错误类型、`SourceProvider` / Repository / `PlaybackController` 接口（签名冻结） |
| 设计系统 | 浅色 / 深色 / 纯黑三套配色、六级字体、形状、间距 token、状态栏图标随主题切换 |
| UI | 单 Activity + App 级 Scaffold + 4 Tab + 常驻 MiniPlayer + 全屏播放页 + 全部子路由占位（无导航死角） |
| 红线 | `LanOnlyInterceptor` 在 OkHttp 出口拦截非私网地址；`ci_guard.py` 把「不多申请权限 / 不引统计 SDK / 无明文密钥」变成 CI 门禁 |
| CI/CD | `ci.yml`（PR 门禁）、`release.yml`（tag → 签名 APK → GitHub Release）、Fastfile、版本管理脚本 |
| 测试 | 5 个纯函数单测类（时间格式化、拼音/FTS 转义、格式矩阵、私网判定、结果映射） |

**没做（严格按 P0 范围）**：本地扫描、播放引擎、SMB / WebDAV / DLNA、全局搜索、歌词、均衡器。这些是 P1–P7 的事。

---

## 2. 环境要求

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | AGP 8.7.3 / Gradle 8.9 不支持本机现有的 JDK 25，需安装 Temurin 17 并设 `JAVA_HOME` |
| Android SDK | Platform 35 + Build Tools 35.0.0 | Android Studio Ladybug 或更新 |
| Kotlin / AGP / Gradle | 2.1.0 / 8.7.3 / 8.9 | 已由 wrapper 与版本目录锁定 |
| Git | 任意 | 用于 CI 的 `git ls-files` 密钥检查 |

> 本机默认 JDK 是 25（AGP 8.7.3 / Gradle 8.9 不支持），验证时临时切到 **JDK 21** 并装了临时 Android 35 SDK。你本地请固定 **JDK 17**（CI 也用 17）；首次同步需下载依赖（约 5–10 分钟）。真机安装与商店提审仍需你本人在设备/CI 上完成。

### 本地构建

```bash
export JAVA_HOME=/path/to/jdk-17      # Windows: set JAVA_HOME=C:\...\jdk-17
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest     # 5 个测试类应全绿
python3 scripts/ci_guard.py --check all
```

Windows 用 `gradlew.bat :app:assembleDebug`。

### P0 自测 5 条

| # | 操作 | 预期 |
|---|---|---|
| 1 | 安装后启动 | 图标为音符、名称「轻听」、冷启动无白屏（`adb shell am start -W` TotalTime ≤ 2s） |
| 2 | 点底部 4 个 Tab | 切换有 180ms 过渡，选中态图标填充 + 主色 |
| 3 | 音乐库点分段标签 / 两个按钮 | 标签可切；按钮弹 Snackbar「P1 实现」 |
| 4 | 点 MiniPlayer → 播放页 → 点下拉箭头 | 播放页滑入，返回后底部栏与 MiniPlayer 恢复 |
| 5 | 设置 → 主题 → 切「浅色 / 深色 / 纯黑」→ 杀进程重进 | 状态栏图标颜色随主题变化，选择被持久化 |

再补两条：系统字体调到 1.3× 观察空状态与底部栏不重叠；旋转 / 分屏不崩。

---

## 3. CI/CD 上线路径

```
push / PR ──> ci.yml ──> 红线守卫 → assembleDebug → 单测 → Lint → 上传 APK + 报告
                                        │
                                        └─ 全绿才可合并 main

改 version.properties → commit → git tag v1.0.0 → push tag
        │
        ▼
release.yml ──> 版本一致性校验 → 红线守卫 → 单测门禁
             → 从 Secrets 解码 keystore → assembleRelease（强制验签）
             → apksigner verify → 包体 ≤ 20MB → aapt badging → 产物权限白名单审计
             → 重命名 + SHA-256 → 上传 Artifact（含 R8 mapping）
             → softprops/action-gh-release 发布 GitHub Release
```

分发目标：**GitHub Release 自分发 APK**（手机直接下载安装），不接 Google Play / 国内商店。

### 首次配置（✅ 已完成，2026-11-13）

以下步骤已全部执行完毕，保留在此作为**密钥轮换 / 换机复现**的参考：

- GitHub 账号 `naboluoye`，公开仓库 `litelisten`，默认分支 `main`。
- 正式密钥 `litelisten.jks`（RSA 4096 / 有效期 10000 天 / 别名 `litelisten`）放在**工程目录之外**，未进入 Git；口令请从离线备份取回。
- 4 个 Secrets 已写入仓库：`ANDROID_KEYSTORE_BASE64`、`LITELISTEN_KEYSTORE_PASSWORD`、`LITELISTEN_KEY_ALIAS`、`LITELISTEN_KEY_PASSWORD`。
- `v1.0.0` tag 已推送，`Release` 工作流已完成并发布 APK。

**① 生成签名密钥（在你自己的电脑上，密码自己保管，绝不入库）**

```bash
keytool -genkeypair -v -keystore litelisten.jks -alias litelisten \
  -keyalg RSA -keysize 4096 -validity 10000
```

> 这个密钥决定以后能否覆盖升级。**丢了就无法给已装用户发新版**，请离线备份。

**② 把密钥转成 base64 并填入 GitHub Secrets**

```bash
# macOS / Linux
base64 -i litelisten.jks | pbcopy
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("litelisten.jks")) | Set-Clipboard
```

仓库 → Settings → Secrets and variables → Actions → New repository secret，添加 4 个：

| Secret | 值 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 上面复制的整串 base64 |
| `LITELISTEN_KEYSTORE_PASSWORD` | keystore 口令 |
| `LITELISTEN_KEY_ALIAS` | `litelisten` |
| `LITELISTEN_KEY_PASSWORD` | key 口令（与 store 口令相同则填同一个） |

**③ 本地 Release 构建（可选）**：复制 `keystore.properties.example` 为 `keystore.properties`，填入真实值，运行 `./gradlew :app:assembleRelease`。该文件已在 `.gitignore`。

**④ 推送仓库**：已完成。本地仓库已关联 `origin`，`main` 分支与 `v1.0.0` tag 均已推送。

#### 云端流水线踩坑记录（已修复，勿回退）

| 现象 | 原因 | 修法 |
|---|---|---|
| `setup-android@v3` 20 秒失败：`Failed to find package 'tools'` | 该 action 会去装 Google 已下架的旧 SDK 包，与新版 GitHub runner 的 cmdline-tools 16.0 不兼容 | 两个 workflow 都改为**直接用 runner 预装 Android SDK**，显式设 `ANDROID_HOME` 并动态定位 `sdkmanager` |
| `./gradlew: Permission denied`（exit 126） | Windows 提交时不保留可执行位，`gradlew` 以 `100644` 入库 | `git update-index --chmod=+x gradlew`，并新增 `.gitattributes` 固定 `gradlew text eol=lf` |

### 每次发版 SOP

```bash
python3 scripts/bump_version.py 1.0.1     # versionCode 自动 +1，versionName 改为 1.0.1
git add version.properties && git commit -m "release: 1.0.1"
git tag -a v1.0.1 -m "Release 1.0.1"
git push origin main --tags               # 推送 tag 后 release.yml 自动跑
```

**版本一致性是硬门禁**：tag 与 `version.properties` 不一致会直接失败并提示先跑 `bump_version.py`。也可在 Actions 页手动触发 `Release`，填 `version` 输入框（不创建 tag 也能出包，Release 会指向该 tag）。

**回滚**：GitHub Release 页删除对应 Release / tag；重新用旧 tag 触发即可重建。密钥泄露时立刻换 keystore（已装用户将无法覆盖升级，需卸载重装）——所以备份和保密优先于一切。

### CI 里的红线守卫 `scripts/ci_guard.py`

| 检查 | 拦什么 |
|---|---|
| `permissions` | Manifest 权限必须与 11 项白名单**完全一致**，多一个少一个都失败 |
| `dependencies` | 禁止 firebase / crashlytics / 广告 / 统计 / GMS / cling / jcifs |
| `lan` | `LanOnlyInterceptor` 必须存在且被 `OkHttpProvider` 挂载，防止出现绕过公网校验的出口 |
| `source` | 禁止 `TODO()`、`NotImplementedError(`、`GlobalScope`、`Thread.sleep`、`println`、`Log.d`；禁止 UI 层硬编码中文 |
| `build` | 锁定 applicationId / minSdk / targetSdk / minify / 强制验签 |
| `secrets` | 检查是否有 `.jks` / `.pem` / `keystore.properties` 被提交进 git |
| `version` | tag 与 version.properties 一致 |
| `apk` | 对**打包产物**跑 `aapt dump badging`，权限必须落在「11 项主动申请 + 2 项已审计库注入」白名单内，多一个就失败 |

Fastlane 只作为**本地一键加速**（`bundle install && bundle exec fastlane android test|debug|release`）；CI 直接调 Gradle，减少 Ruby 依赖带来的不确定性。

---

## 4. 与文档规格的差异（都是有依据的主动修正）

| # | 文档写的 | 实际做法 | 原因 |
|---|---|---|---|
| 1 | 依赖清单不含 Material Components | 加了 `com.google.android.material:material:1.12.0` | 实测 Compose Material3 的 AAR **不含** `Theme.Material3.DayNight.NoActionBar` 这个 XML 父样式；不加会 aapt 报错。仅用于提供主题父样式，UI 仍全 Compose |
| 2 | `tinypinyin 2.0.3` | 额外加阿里云 JCenter 镜像仓库并 `content { includeGroup(...) }` 限制 | 2.0.3 不在 Maven Central / JitPack（JitPack 上同名构建失败）；只对这一个 group 生效，不污染其他依赖 |
| 3 | 未指定 slf4j-nop 版本 | 锁 `2.0.9` | 实测 smbj 0.14.0 依赖 `slf4j-api:2.0.9`，版本不一致会绑不上、运行期刷警告 |
| 4 | P0 主题默认 `DARK` | 默认 `SYSTEM`（可切浅/深/纯黑） | 《05 契约》§10 规定 `theme_mode` 默认 `system`；且系统深色下即深色，验收「状态栏随主题变化」可真机验证 |
| 5 | 建「方法抛 `NotImplementedError` 的空实现」 | 只冻结接口，不生成会抛异常的实现类 | 抛异常的空实现是运行期地雷，且违反基线「不写 TODO() 掉的主流程」；P1–P6 各自接真实实现 |
| 6 | `LibraryUiState.Content` 含 `LazyPagingItems` | 该文件延后到 P1 创建 | 它是 Compose 类型，放进「纯 Kotlin、不依赖 Android」的 `core/model` 会破坏分层规则；P1 放在 `ui/library/` 下按原签名建 |
| 7 | 阶段提示词要求「第一条回复不写代码」 | 本次直接落盘 | 你这次明确要求「从 0 搭建 + 配好 CI/CD」，等价于已下达开始指令 |
| 8 | P7 才处理混淆 | P0 就预置 R8 规则并**真跑一次 Release** | 实测发现 smbj 的传递依赖 `mbassador` 引用 Android 不存在的 `javax.el.*`，R8 严格模式直接失败；已加针对性 `-dontwarn javax.el.**`，没有关闭混淆 |
| 9 | Manifest「权限一次声明完毕，无多余权限」 | 源 Manifest 严格 11 项；**合并后 APK 多 2 项** | `RECEIVE_BOOT_COMPLETED`（WorkManager 开机重调度）与 `<包名>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（androidx.core 在 API≤32 模拟 `RECEIVER_NOT_EXPORTED`）由库注入，均为普通/签名级、不弹窗。删除会破坏旧系统动态接收器保护与 WorkManager 重启续跑，故保留并白名单化，`ci_guard.py --check apk` 会对任何**白名单外**新增权限直接失败。若坚持严格 11 项，可在 Manifest 加 `tools:node="remove"`，但需接受上述代价 |
| 10 | Lint 警告 ≤ 20 条 | 禁用 `GradleDependency` / `AndroidGradlePluginVersion` | 84 条是「有新版本可用」，而版本被架构文档冻结；其余按 lint 建议补齐：单色图标、`data_extraction_rules.xml`（零外传）、`tools:targetApi`、明文配置的 `tools:ignore`。源码编译本身 0 警告 |
| 11 | `values-en` 留空 / `mipmap-anydpi-v26` | 保留空英文目录；图标目录改为 `mipmap-anydpi` | minSdk 26 让 `-v26` 限定符冗余（lint `ObsoleteSdkInt`） |

另外有 1 处**已知待修**：`core/model/Track.kt` 的 `displayArtist/displayAlbum` 保留了契约里的中文兜底串。`ci_guard.py` 对它白名单告警，P1 接入 UI 时应改为 `strings.xml` 资源。

---

## 5. GitHub 开源参考（只取思路，未复制代码）

| 项目 | License | 借鉴内容 |
|---|---|---|
| [Yasinjahantigh/LogaRhythm-Android](https://github.com/Yasinjahantigh/LogaRhythm-Android) | Apache-2.0 | 离线优先播放器的 CI 结构；env 驱动签名、`apksigner verify` 验签、tag → GitHub Release 全链路 |
| [District1655/WebDavMusicPlayer](https://github.com/District1655/WebDavMusicPlayer) | 未声明 | `OkHttpDataSource` + `DefaultDataSource.Factory` 的 WebDAV 流播要点，尤其 `callTimeout(0)` 防长音频被掐断（已写进 `OkHttpProvider.streaming()` 注释） |
| [PixelPlayerHQ/PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) | **GPL-3.0** | 仅看 CI 布局；**未复制任何代码、未引入任何依赖**（你的文档禁止 GPL） |
| [cwuom/NeriPlayer](https://github.com/cwuom/NeriPlayer) | **GPL-3.0** | 仅看模块化拆分思路；同上，未复制 |

所有依赖仍严格为 Apache-2.0 / MIT / BSD，未引入 GPL 组件。

---

## 6. 目录速览

```
litelisten/
├─ .github/workflows/{ci.yml, release.yml}     CI 门禁 / 签名发版
├─ .gitattributes                              gradlew 可执行位与 LF 换行（Linux runner 必需）
├─ scripts/{ci_guard.py, bump_version.py}      红线守卫 / 版本自增
├─ fastlane/{Fastfile, Appfile} + Gemfile      本地一键构建（可选）
├─ gradle/libs.versions.toml                   唯一版本源；gradle/wrapper/ 齐全
├─ version.properties                          versionCode / versionName
├─ keystore.properties.example                 本地签名模板（真实文件已 gitignore）
├─ app/build.gradle.kts                        构建 / 签名 / R8 / 依赖
└─ app/src/main/
   ├─ AndroidManifest.xml                      11 项权限一次性声明完毕
   ├─ res/{values, values-night, values-en, drawable, mipmap, mipmap-anydpi, xml}
   └─ java/com/litelisten/player/
      ├─ LiteListenApp.kt / MainActivity.kt
      ├─ di/                                   6 个 Hilt Module
      ├─ core/{model, common, designsystem, datastore, source/api, network}
      ├─ data/repository/                      仅冻结接口
      └─ ui/{navigation, library, browse, search, player, settings}
```

---

## 7. 已知限制 / 下一步

- **未在真机安装验证**：本机没有可连接的 Android 设备，冷启动 ≤ 2s、4 Tab 切换、大字体 1.3×、旋转/分屏等 §2 自测项仍需你在小米 14 Pro 上逐条勾 `16_验收清单与测试矩阵.md`。
- 本机验证用的是临时 SDK（JDK 21）；云端 Release 已改用正式密钥。**正式签名密钥与口令丢失会导致已装用户无法覆盖升级**，请务必离线备份（文件在工程目录之外，未入库）。
- `LanAddress` 目前按字符串判断私网，不做 DNS 解析：SMB / WebDAV 主机请填 **IP** 或 `.local` 名称。P3 会补「解析后校验 IP」的变体。
- `connectedDebugAndroidTest`、P7 的 7 个测试类、真机全流程回归矩阵属于后续阶段，CI 当前只跑单测 + Lint + 产物审计。
- GitHub Actions 已实跑通过：每次 push `main` 自动跑 `CI`，每次推 `v*` tag 自动跑 `Release` 并发布到 GitHub Release。首次上线的两次失败及修复见 §3 踩坑记录。
- 建议下一阶段：按 `08_提示词_P1_本地曲库.md` 推进，并在合并前确认 `ci.yml` 全绿。
