# 轻听 LiteListen · Android 局域网音乐播放器

> **当前版本：v2.0.4（DLNA 群晖连接修复版，推荐安装）** ｜ 仓库：https://github.com/naboluoye/litelisten
> [v2.0.4 Release 下载页](https://github.com/naboluoye/litelisten/releases/tag/v2.0.4) ｜ [v2.0.3（上一版）](https://github.com/naboluoye/litelisten/releases/tag/v2.0.3) ｜ [v2.0.2](https://github.com/naboluoye/litelisten/releases/tag/v2.0.2) ｜ [v1.0.0（历史骨架版）](https://github.com/naboluoye/litelisten/releases/tag/v1.0.0)
> 包名 `com.litelisten.player` ｜ minSdk 26（Android 8.0）/ targetSdk 35 ｜ Kotlin 2.1.0 + Compose + Media3 + Room
> 零账号、零广告、零遥测、零外网请求：所有网络目标在请求发出前强制校验为局域网私网地址。

**版本沿革**：v1.0.0 只是 P0 工程骨架；**v2.0.0 把 P1–P7 全部实现为真实功能**；v2.0.1 修掉「装完曲库为空」「WebDAV 子目录 404」等运行时缺陷；v2.0.2 修复局域网设备发现与根目录浏览；v2.0.3 新增 Jellyfin 媒体服务器接入、把账号密码前置到添加向导、并加入无损高保真播放；**v2.0.4 修复 Jellyfin 扫描到 0 首、DLNA/群晖连接失败，并加入出品方文案，是当前推荐安装的版本**。所有版本使用同一把签名密钥，**已装旧版可直接覆盖升级，数据不丢**。

### v2.0.4 新增与修复（实机反馈）

| 反馈 | 根因 | 这一版做了什么 |
|---|---|---|
| Jellyfin（如 `SRV-2019`）能识别、但「加入曲库」扫描结果是 0 首 | 旧版扫描走层级 `walk`：先列 `/Users/{id}/Views`（媒体库），再逐层按 `ParentId` 浏览。Jellyfin 音乐库顶层往往是 `MusicArtist`/`UserView`，而某些账号配置下 `Views` 或层级 `Items` 查询直接返回空，导致递归到底一个音频都没拿到 | 扫描改成直接调 Jellyfin 的**递归搜索接口** `Recursive=true&IncludeItemTypes=Audio`，一次性拉取该账号能访问的全部音频，完全绕开 Views / 层级结构。手动翻目录浏览仍走层级，不影响使用 |
| DLNA 媒体服务器（群晖 `swf_nas`）搜得到却连不上，填用户名密码也没用 | ① DLNA/UPnP 是**匿名协议**，旧版添加表单却对 DLNA 也强制要求账号密码，用户被卡死在凭据上；② DLNA 的 SOAP 控制请求默认走系统「默认网络」，手机在「Wi-Fi 无互联网 + 开蜂窝」时默认网络是蜂窝，192.168.x.x 的控制地址全部不可达 | ① DLNA 添加表单**不再要求、也不再发送**账号密码（已加单测锁定，防回退）；② 新增 `LocalNetworkHolder`，发现阶段记录真正的局域网 Network，DLNA 的控制请求、设备描述请求都强制绑定这张网卡，杀进程重启后也能即时解析；③ SOAP 控制请求补 `Accept` / `Connection` 头，增强群晖兼容 |
| 希望 App 里说明出品方 | — | 设置页「关于」区加入出品方文字：「本软件由风起时出品，功能是为解决手机本地音频的播放，免费无广告，如有需要敬请使用」 |

**DLNA / 群晖实操提示**：群晖 DSM 的「媒体服务器」默认就是匿名 DLNA，直接点「测试连接」即可，不用填凭据；若仍连不上，多半是手机与 NAS 不在同一 Wi-Fi、或路由器开了 AP 隔离，可在添加向导里手动填设备控制地址（形如 `http://192.168.x.x:端口/…/Control`，端口通常是 50001/8200 之类）。

### v2.0.3 新增与修复（实机反馈）

| 反馈 | 根因 | 这一版做了什么 |
|---|---|---|
| 服务器 `192.168.88.51` 装了 Jellyfin，但「来源」里搜不到里面的歌 | Jellyfin 不提供 SMB / WebDAV 文件共享，走的是自己的 HTTP API（默认 8096）；旧版扫描端口表里也根本没有 8096 | 新增 **Jellyfin 原生来源**：账号密码换 token → 列出媒体库 / 艺术家 / 专辑 / 歌曲 → 加入曲库后全局搜索即可搜到。局域网扫描新增 8096 / 8920，并用免登录的 `/System/Info/Public` 确认后显示服务器名；同一台机器同时开 SMB 和 Jellyfin 会分别列出 |
| 添加 NAS 时没要求输入用户名和密码 | 账号密码被折叠在表单最底部、且排在「自动查找共享」之后，用户先点查找必然因匿名连接失败 | 凭据区**前置到地址之后**、默认必填并加人话说明；匿名改为显式勾选「这台服务器不需要登录」；SMB / WebDAV / Jellyfin 统一校验，Jellyfin 强制账号密码。凭据仍用 Android Keystore 加密存储 |
| 希望可以播放 HiFi 高品质音乐 | 旧版虽能读 FLAC，但默认按 16 bit PCM 输出，24 bit / 96 kHz 会被降位 | 开启 **32 位浮点输出**（设置 → 音效 → 高保真输出，默认开启）+ 解码器回退 + 无损更大预读缓冲；播放页显示真实 `格式 · 采样率 · 位深 · 声道` 与「Hi-Res / 无损原音质」标签。Jellyfin 走 `static=true` 原始流，服务器不转码、不降码率 |

**Jellyfin 兼容性要点**：认证同时带 `Authorization: MediaBrowser Token="…"`（Jellyfin 12 起只认这个）与 `X-Emby-Token`（旧版/兼容实现）；播放用 `/Audio/{id}/stream?static=true` 而不是 `/Items/{id}/Download`，后者会被账号的「允许下载」权限挡成 403。token 只经拦截器注入请求头，**不写进 URI、不落数据库、不进播放队列快照**。

**HiFi 能力边界（如实说明）**：FLAC / WAV / AIFF / ALAC 支持无损原文件直传；APE / DSD(.dsf/.dff) / WMA 仍不支持（解码器依赖，界面会灰显）。是否真的「bit-perfect」还取决于手机 DAC / 蓝牙编码 / 系统混音，本机无法测量；**开启均衡器、低音增强或变速播放时声音仍会被重新处理**，追求原音质时请关闭音效。

### v2.0.2 修了什么（实机反馈）

| 问题 | 影响 | 修复 |
|---|---|---|
| 「扫描局域网设备」只发 SSDP 搜 DLNA 媒体服务器 | 普通 Windows 电脑 / NAS 文件共享不响应 M-SEARCH，发现列表永远为空 | 新增本机 Wi-Fi 子网端口探测（445/139/5000/5001/5005/8080/8081/8800/80/443）+ NetBIOS 名称查询，真正列出局域网里的电脑与 NAS；页面会显示扫描范围如 `192.168.1.0/24` |
| 裸 Socket 未绑定 Wi-Fi 网络 | Wi-Fi 无互联网且开着蜂窝数据时，Android 把内网探测路由到蜂窝，一台也扫不到 | 端口探测与 NBNS 查询显式绑定到 Wi-Fi / 以太网 `Network`；SSDP 同时修好接口绑定、组播锁与等待时长 |
| 浏览器把错误参数写死为空字符串 | 根目录失败时只显示「无法识别的内容（）」，真实原因全被吞掉 | `UiMessage` 统一给出可读文案与参数，区分 HTTP 状态码、非 WebDAV 地址、空响应、SMB 状态、目录读取失败，并提示下一步怎么做 |
| 地址填成 NAS 网页首页 | 返回 200 + HTML，被当 XML 解析后只显示「文件夹是空的」 | 非 XML / 非 multistatus 响应明确报「这个地址不是 WebDAV 目录」 |
| SMB「测试连接」吞掉列目录异常 | 测试成功，一进根目录就报错 | 测试与真实浏览走同一条路径，失败如实上报 |
| 只发现 IP，却要用户手填共享名 | 连不上、不知道填什么 | 向导新增「自动查找共享」，列出这台机器上可用的共享名，点一下自动填入；Windows / 群晖 / 威联通 / 中文共享名均覆盖 |
| 其它 | DLNA SOAP 响应容错、WebDAV 405 明确提示、SMB 错误码翻译、发现页进页面即扫描 | 均已修正 |

### v2.0.1 修了什么

| 问题 | 影响 | 修复 |
|---|---|---|
| 授权成功后不自动扫描 | 装完打开曲库一直是空的，必须自己找到「扫描」按钮 —— 正是上一版实机退货的复现路径 | 授权回调、冷启动、进入曲库页三处都会检查「已授权且本地曲库为空」并自动建库 |
| WebDAV 二级目录路径丢父层级 | 进入子文件夹后点歌 404、整目录导入路径错位 | `href` 统一相对 `config.url` 的 path 计算，与 SMB 累积路径坐标系对齐 |
| 只认 IP / `.local`，短主机名被当公网拦截 | 填 `nas`、`DISKSTATION` 这类常见设备名时测试连接必失败 | 短主机名先解析，全部地址落在私网段才放行；解析到公网仍拒绝 |
| 冷启动网络源异步恢复时的竞态 | 从曲库点已入库的 SMB / WebDAV 歌曲偶发失败或 401 | 播放网络曲目与恢复队列前等待 hydrate 完成（带超时兜底） |
| 播放器未就绪时点歌无反馈 | 点击后什么都不发生 | 显示「播放器正在启动，请再点一次播放」并自动重连 |
| 其它 | 重建索引失败留下空曲库、SAF 空目录不清旧记录、HTTPS WebDAV 删除后认证头残留、歌词/文件名 `+` 被解成空格 | 均已修正 |

---

## 扫码下载（v2.0.4）

![轻听 v2.0.4 扫码下载](docs/qr-download.png)

- **扫码直达 APK**：<https://github.com/naboluoye/litelisten/releases/download/v2.0.4/LiteListen-v2.0.4.apk>
- 版本说明与 SHA-256：[GitHub Release v2.0.4](https://github.com/naboluoye/litelisten/releases/tag/v2.0.4)
- 可转发的下载卡片：[docs/qr-download-card.png](docs/qr-download-card.png)
- 下载后在系统里允许「安装未知来源」即可安装。二维码采用 30% 纠错等级并保留静默区，缩放或打印后仍可识别（已用解码器实测校验）。

---

## 1. 现在能做什么

| 模块 | 实际功能 |
|---|---|
| 本机曲库 | 首次进入申请读取音频权限；扫描 MediaStore 全部音乐，过滤系统音/0 字节/短音频（可开关）；按**歌曲 / 专辑 / 艺术家 / 文件夹**四视图浏览，支持排序、升降序、按来源筛选 |
| 目录扫描 | 「＋」选一个文件夹，通过 SAF 递归索引（不修改、不删除用户文件）；曲库页常驻「重新扫描」按钮，随时可重扫 |
| 播放 | Media3 `MediaSessionService` 后台播放，通知栏 / 锁屏 / 蓝牙按键控制；队列拖拽与删除、随机、单曲/列表循环、0.5–2× 倍速、10 秒快进退、睡眠定时（最后 10 秒淡出）、杀进程后恢复队列与进度 |
| 高保真 | 32 位浮点输出避免 24 bit 降位、解码器回退、无损更大预读；播放页实时显示解码出的格式 / 采样率 / 位深 / 声道与 Hi-Res 标签（设置 → 音效 → 高保真输出，默认开启） |
| 网络源 | **SMB**（smbj 随机读 DataSource，支持拖动进度）、**WebDAV**（PROPFIND 目录浏览 + OkHttp Range 流播）、**DLNA/UPnP**（SSDP + ContentDirectory Browse）、**Jellyfin**（REST API 登录 + 媒体库浏览 + `static=true` 原始无损流）；均可测试连接、浏览目录、直接播放、整目录导入曲库 |
| 凭据 | 用户名/密码用 Android Keystore AES-256-GCM 加密后存储，明文不落盘、不进 URI、不进日志；地址不合规直接拒绝连接 |
| 搜索 | 中文原名 / 全拼 / 首字母混合索引，空格分词 AND 语义；同时搜歌曲、专辑、艺术家；保留最近 10 条搜索历史 |
| 其它 | 收藏、最近播放、LRC 歌词（自动找同名文件 + 手动选择 .lrc，点击歌词行跳转）、均衡器与低音增强（挂到真实 audioSessionId）、首次使用 3 页引导、浅色/深色/纯黑主题、动态取色 |
| 设置 | 主题、动态取色、断点恢复、自动播放、拔耳机暂停、淡出、出错自动跳过、倍速、**高保真输出**、扫描短音频、仅 Wi-Fi、连接超时、缓冲时长、缓存上限、重建索引、清理封面缓存 |

所有设置项都接了真实逻辑：超时/缓冲/缓存/仅 Wi-Fi/拔耳机暂停/淡出经 `RuntimeConfig` 快照驱动 OkHttp、ExoPlayer、拦截器与封面缓存；下次新建连接或播放器时生效，不打断正在播放的音频。

---

## 2. 上手三步（用户视角）

1. **装好打开**：首次启动是 3 页引导，完成后按提示授予「读取音频」权限；**授权成功即自动扫描本机音乐**，曲库直接就有歌，不用再找扫描按钮。
2. **想重扫或只加某个目录**：曲库页右上角的 ⟳ 是重新扫描，空曲库时也有「扫描本机音乐」按钮；点 ＋ 可只选一个文件夹索引。
3. **加网络服务器**：底部「来源」页**进入即自动扫描本机 Wi-Fi 子网**，「发现的设备」里会列出开着文件共享的电脑 / NAS 和 Jellyfin 媒体服务器；点右侧「添加」会带着 IP 跳到添加向导。**先填用户名和密码**（就是你在 NAS / Jellyfin 上设置的账号），再点「自动查找共享」选共享名，最后「测试连接」→「保存」。保存后进目录即可播放，或「加入曲库」整目录导入，之后在「搜索」页就能搜到服务器里的歌。也可以右上角 ＋ 手动添加 SMB / WebDAV / Jellyfin。
4. **连 Jellyfin**：右上角 ＋ →「添加 Jellyfin 媒体服务器」，填服务器 IP（如 `192.168.88.51`）、端口 `8096`（HTTPS 用 `8920`）、以及 Jellyfin 的登录账号密码 → 测试连接 → 保存。进去后按「媒体库 → 艺术家 / 专辑 → 歌曲」逐层浏览；点「加入曲库」可整库导入。歌曲按服务器上的原文件播放，不会被转成低音质。

> 扫描只覆盖**手机当前所在的子网**（页面会显示范围，例如 `192.168.1.0/24`）；如果服务器在另一个网段、连的是访客网络，或路由器开了 AP 隔离，扫描列表里不会出现，但**仍可手动填 IP 添加**。服务器地址可填局域网 IP（如 `192.168.1.10`）、`.local` 名称，或 NAS / 路由器的短主机名（如 `nas`）；短主机名会先解析并确认落在私网段才放行，公网地址一律拦截，这是刻意设计，不是故障。不需要账号的共享请勾「这台服务器不需要登录」。

---

## 3. 已真实跑通的验证（v2.0.4）

| 项目 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ **11 个测试类 / 48 条用例，0 失败**（新增 Jellyfin JSON 解析、静态原流地址、端口分类、凭据校验顺序、无损/Hi-Res 判定单测，及 DLNA 免登录单测） |
| `./gradlew :app:lintDebug` | ✅ 0 error |
| `./gradlew :app:assembleRelease`（R8 混淆 + 资源压缩） | ✅ BUILD SUCCESSFUL；云端正式签名产物体积与 SHA-256 见 [Release v2.0.4](https://github.com/naboluoye/litelisten/releases/tag/v2.0.4) |
| `apksigner verify --verbose`（云端流水线） | ✅ 沿用同一把 RSA 4096 密钥，证书 SHA-256 `cc7b03e7…e7ac`，与 v1.0.0 一致，可覆盖升级 |
| `ci_guard.py --check all` / `--check apk` | ✅ PASS，合并后 Manifest 权限 = 11 项主动申请 + 2 项已审计库注入 |
| GitHub Actions `CI` / `Release` | tag `v2.0.4` 触发签名发版，结果见仓库 Actions |

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
改 version.properties → commit → git tag v2.0.4 → push tag
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
python3 scripts/bump_version.py 2.0.4     # versionCode 自动 +1
git add version.properties && git commit -m "release: 2.0.4"
git tag -a v2.0.4 -m "Release 2.0.4"
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
├─ docs/{qr-download.png, qr-download-card.png}  v2.0.4 扫码下载图
├─ scripts/{ci_guard.py, bump_version.py}      红线守卫 / 版本自增
├─ gradle/libs.versions.toml                   唯一依赖版本源
├─ version.properties                          versionCode=6 / versionName=2.0.4
├─ app/proguard-rules.pro                      R8 / kotlinx.serialization 规则
└─ app/src/main/java/com/litelisten/player/
   ├─ core/{model, common, designsystem, database, datastore, network, scan, player}
   ├─ core/source/{api, smb, webdav, dlna, jellyfin, discovery}  四类网络源 + 局域网发现 + 凭据加密
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
- **HiFi 边界**：高保真输出解决的是 App 内部的降位与转码，最终音质仍受手机 DAC、系统混音、蓝牙编码（SBC/AAC/LDAC）限制；APE / DSD / WMA 无解码器，不支持。Jellyfin 自签名 HTTPS（8920）需要服务器使用受信任证书，局域网自签名证书不受支持。
- `connectedDebugAndroidTest` 仪器化测试与真机全流程回归矩阵仍未接入 CI。
