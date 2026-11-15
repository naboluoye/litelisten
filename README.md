# 轻听 LiteListen · Android 局域网音乐播放器

> **当前版本：v2.0.8（曲库管理测试版：重置 / 按文件夹 / 自建歌单）** ｜ 仓库：https://github.com/naboluoye/litelisten
> [v2.0.8 Release 下载页](https://github.com/naboluoye/litelisten/releases/tag/v2.0.8) ｜ [v2.0.5（正式版，推荐稳定使用）](https://github.com/naboluoye/litelisten/releases/tag/v2.0.5) ｜ [v2.0.7](https://github.com/naboluoye/litelisten/releases/tag/v2.0.7) ｜ [v2.0.6](https://github.com/naboluoye/litelisten/releases/tag/v2.0.6) ｜ [v2.0.4](https://github.com/naboluoye/litelisten/releases/tag/v2.0.4) ｜ [v2.0.3](https://github.com/naboluoye/litelisten/releases/tag/v2.0.3) ｜ [v2.0.2](https://github.com/naboluoye/litelisten/releases/tag/v2.0.2) ｜ [v1.0.0（历史骨架版）](https://github.com/naboluoye/litelisten/releases/tag/v1.0.0)
> 包名 `com.litelisten.player` ｜ minSdk 26（Android 8.0）/ targetSdk 35 ｜ Kotlin 2.1.0 + Compose + Media3 + Room
> 零账号、零广告、零遥测；本地/局域网音乐播放与曲库管理全程离线。可选的联网功能只有两个，都必须由你**主动点击**才发一次请求：播放页「AI 介绍」（v2.0.6 起）与歌词页「AI 讲解歌词」（v2.0.7 起），不点就完全离线；服务配置由构建期注入，仓库与文档不含任何服务信息。

**版本沿革**：v1.0.0 只是 P0 工程骨架；**v2.0.0 把 P1–P7 全部实现为真实功能**；v2.0.1 修掉「装完曲库为空」「WebDAV 子目录 404」等运行时缺陷；v2.0.2 修复局域网设备发现与根目录浏览；v2.0.3 新增 Jellyfin 媒体服务器接入、把账号密码前置到添加向导、并加入无损高保真播放；v2.0.4 修复 Jellyfin 扫描到 0 首、DLNA/群晖连接失败，并加入出品方文案；v2.0.5 加入无缝播放、ReplayGain 响度均衡、内嵌歌词与逐字歌词，是**稳定的正式版**；v2.0.6 增加播放页「AI 介绍」按钮；v2.0.7 让歌词更准、能跟着唱、AI 能讲歌词；**v2.0.8 是曲库管理测试版：一键重置重扫、按文件夹整理、自建歌单并按歌单播放**。所有版本使用同一把签名密钥，**已装旧版可直接覆盖升级，数据不丢**。

### v2.0.8 新增（测试版 · 曲库管理）

| 新增 | 说明 |
|---|---|
| 重置 | 曲库右上角「⋮ → 清空曲库（重置）」：清空已扫描到的歌曲、歌词与歌单里的歌曲条目，**不删除手机或 NAS 上的任何文件**，歌单名字保留。清空后可重新扫描或按文件夹逐个添加；重置后不会偷偷自动扫回来 |
| 按文件夹 | 「⋮ → 扫描指定文件夹」单独索引一个目录（递归子目录）。文件夹视图现在显示**真正的相对目录**（以前本机音乐会被全部归到 `storage` 一个文件夹里），能一路进到具体专辑目录 |
| 自建歌单 | 曲库新增「歌单」标签，可新建歌单；长按单曲「加入歌单」、专辑页「整张加入歌单」、文件夹页「整个文件夹加入歌单」，重复的歌自动跳过 |
| 歌单详情 | 播放全部 / 随机播放、重命名、删除歌单、把某首移出歌单；只会删歌单，不会删音乐文件 |
| 播放时选歌单 | 播放队列页右上角「载入歌单」：选一个歌单替换当前队列，从第一首开始按你的顺序播 |

边界说明（如实）：「扫描指定文件夹」与「扫描本机音乐」是两个独立来源，如果你先用 MediaStore 整机扫描、又单独添加了同一个目录，那批歌会出现两次（id 不同）。想避免重复，重置后只用其中一种方式整理曲库即可。

### v2.0.7 新增（测试版 · 歌词与跟唱）

| 新增 | 说明 |
|---|---|
| 歌词时间轴修正 | 修掉两处老问题：带 `[offset:]` 的逐字歌词会整体错位；每句没有「结束时间」，导致最后一句永远唱不完。现在逐字时间与行尾都会正确回填 |
| 进度精度提升 | 歌词页进度从 500ms 提升到 **60ms** 采样，逐字染色不再「跳字」 |
| 卡拉 OK 式染色 | 已唱过的字用主色、正在唱的字加粗、没唱的字淡化；普通 LRC（无逐字标签）也会按句子时长做渐进染色 |
| 跟唱模式 | 歌词页右上角「跟唱」开关。开启后：**点任意一句即单句循环**（唱完自动跳回句首）、可切 0.75x / 0.9x 慢速练唱、可 ±0.1 秒微调歌词偏移 |
| 手动歌词持久化 | 以前手动选的 .lrc 重启就丢，现在存进本地数据库，下次直接显示；可随时移除 |
| 共享目录歌词 | NAS / SMB 共享里与歌曲**同目录同名**的 .lrc 现在会自动读取（以前只支持手机本地），中文歌词自动识别 UTF-8 / GBK 编码 |
| 批量导入 | 没歌词时可选一个文件夹，把里面的 .lrc 一次性按文件名匹配进曲库（只接受带时间轴的歌词） |
| 粘贴导入 | 不想下载文件时，把网页上的歌词复制进 App 粘贴保存，自动识别时间轴 |
| AI 讲解歌词 | 歌词页「AI 讲解歌词」按钮：三段讲**歌词大意 / 值得注意的句子 / 怎么唱好**。没有歌词原文时也能用（只讲大意，并明确要求模型不许编造歌词） |

边界说明（如实）：本版**不做**联网自动抓取歌词网站的歌词——那会打破「非私网地址一律拦截」的红线，并带来版权与随时失效的问题，所以走「同目录自动匹配 / 批量导入 / 粘贴导入」三条离线路径。跟唱模式不录音、不开麦克风、不做音准打分（音准打分需要 `RECORD_AUDIO` 权限，与现有权限白名单冲突，留待你明确授权后再评估）。

### v2.0.6 新增（测试版 · AI 介绍）

| 新增 | 说明 |
|---|---|
| 播放页「AI 介绍」按钮 | 点击后根据**正在播放的歌曲**（歌名 + 演唱者）查询并弹出三段介绍：**歌曲背景 / 演唱者介绍 / 歌词简介**；回答风格为轻松口语化、短句、不编造 |
| 三段式展示 | 结果按三段分栏展示并支持滚动；模型未按格式分段时自动展示原文，不会白屏 |
| 错误与限流提示 | 配置异常、请求过快、内容过长、服务故障、断网分别给出大白话提示，支持一键重试；不会把任何技术细节暴露给用户 |
| 隐私与开关 | 该功能默认关闭、不使用即完全离线；只在点击按钮时发起一次请求。设备标识是本机随机生成的匿名 ID，不采集通讯录/位置/账号 |

> 说明：这是**功能测试版**，只想安安静静听歌的话继续用 v2.0.5 即可。服务地址与凭据只在构建期以私有 Secrets 注入，未写进本仓库、二维码或任何公开文档。

### v2.0.5 新增（音频体验升级）

| 新增 | 说明 |
|---|---|
| 无缝播放 | 跳过歌曲首尾静音，连续播放更顺滑（设置 → 音效 → 无缝播放，默认开启）。自带 gapless 元数据的 MP3 可做到采样级无缝；其他格式为「跳过静音」近似无缝 |
| ReplayGain 响度均衡 | 扫描时读取音频文件里的响度标签（FLAC VORBIS_COMMENT、MP3 ID3v2 TXXX），播放时自动拉平歌曲之间的音量。**只压低不放大**，不会产生爆音（设置 → 音效 → ReplayGain，默认开启；无标签的文件不受影响） |
| 内嵌歌词 | 扫描时自动提取文件内嵌歌词（FLAC LYRICS/SYNCEDLYRICS、MP3 USLT），优先级：同名 .lrc 文件 → 内嵌同步歌词 → 手动选择（原有功能全部保留） |
| 逐字歌词 | 支持增强 LRC（`<mm:ss.xx>` 逐字时间戳），播放页当前行内加粗高亮正在唱的字 |
| 关于区补充 | 设置 → 关于新增**联系邮箱**（点击拉起邮件）与**检查新版本**（打开浏览器进 GitHub 下载页看最新版；是跳外部浏览器，App 内仍是零外网请求） |

边界说明（如实）：ReplayGain 与内嵌歌词目前只对本机扫描的文件生效（FLAC / MP3 优先），SMB / WebDAV / DLNA / Jellyfin 等远程来源暂不解析标签；OGG/OPUS/M4A 的标签解析与「按专辑增益」留待后续版本。

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

## 扫码下载（v2.0.8 测试版）

![轻听 v2.0.8 扫码下载](docs/qr-download.png)

- **扫码直达 APK**：<https://github.com/naboluoye/litelisten/releases/download/v2.0.8/LiteListen-v2.0.8.apk>
- 版本说明与 SHA-256：[GitHub Release v2.0.8](https://github.com/naboluoye/litelisten/releases/tag/v2.0.8)
- 可转发的下载卡片：[docs/qr-download-card.png](docs/qr-download-card.png)
- 下载后在系统里允许「安装未知来源」即可安装。二维码采用 30% 纠错等级并保留静默区，缩放或打印后仍可识别（已用解码器实测校验）。

---

## 1. 现在能做什么

| 模块 | 实际功能 |
|---|---|
| 本机曲库 | 首次进入申请读取音频权限；扫描 MediaStore 全部音乐，过滤系统音/0 字节/短音频（可开关）；按**歌曲 / 专辑 / 艺术家 / 文件夹 / 歌单**五视图浏览，支持排序、升降序、按来源筛选、一键重置 |
| 目录扫描 | 「⋮ → 扫描指定文件夹」选一个文件夹，通过 SAF 递归索引（不修改、不删除用户文件）；曲库页常驻「重新扫描」按钮，随时可重扫 |
| 自建歌单 | 新建 / 重命名 / 删除歌单；单曲、整张专辑、整个文件夹都能一次加入；歌单详情可播放全部、随机播放、移出单曲；队列页可「载入歌单」替换队列 |
| 播放 | Media3 `MediaSessionService` 后台播放，通知栏 / 锁屏 / 蓝牙按键控制；队列拖拽与删除、随机、单曲/列表循环、0.5–2× 倍速、10 秒快进退、睡眠定时（最后 10 秒淡出）、杀进程后恢复队列与进度；**无缝播放**（跳过首尾静音）与 **ReplayGain 响度均衡**（按标签拉平音量，只压低不放大） |
| 高保真 | 32 位浮点输出避免 24 bit 降位、解码器回退、无损更大预读；播放页实时显示解码出的格式 / 采样率 / 位深 / 声道与 Hi-Res 标签（设置 → 音效 → 高保真输出，默认开启） |
| 网络源 | **SMB**（smbj 随机读 DataSource，支持拖动进度）、**WebDAV**（PROPFIND 目录浏览 + OkHttp Range 流播）、**DLNA/UPnP**（SSDP + ContentDirectory Browse）、**Jellyfin**（REST API 登录 + 媒体库浏览 + `static=true` 原始无损流）；均可测试连接、浏览目录、直接播放、整目录导入曲库 |
| 凭据 | 用户名/密码用 Android Keystore AES-256-GCM 加密后存储，明文不落盘、不进 URI、不进日志；地址不合规直接拒绝连接 |
| 搜索 | 中文原名 / 全拼 / 首字母混合索引，空格分词 AND 语义；同时搜歌曲、专辑、艺术家；保留最近 10 条搜索历史 |
| 其它 | 收藏、最近播放、LRC 歌词（自动找同名文件 → 共享目录同名文件 → 内嵌歌词 → 手动挂载，点击歌词行跳转，逐字染色与跟唱模式）、均衡器与低音增强（挂到真实 audioSessionId）、首次使用 3 页引导、浅色/深色/纯黑主题、动态取色 |
| 设置 | 主题、动态取色、断点恢复、自动播放、拔耳机暂停、淡出、出错自动跳过、倍速、**高保真输出**、**无缝播放**、**ReplayGain 响度均衡**、扫描短音频、仅 Wi-Fi、连接超时、缓冲时长、缓存上限、重建索引、清理封面缓存；关于区含出品方说明、联系邮箱、检查新版本 |

所有设置项都接了真实逻辑：超时/缓冲/缓存/仅 Wi-Fi/拔耳机暂停/淡出经 `RuntimeConfig` 快照驱动 OkHttp、ExoPlayer、拦截器与封面缓存；下次新建连接或播放器时生效，不打断正在播放的音频。

---

## 2. 上手四步（用户视角）

1. **装好打开**：首次启动是 3 页引导，完成后按提示授予「读取音频」权限；**授权成功即自动扫描本机音乐**，曲库直接就有歌，不用再找扫描按钮。
2. **想重扫或只加某个目录**：曲库页右上角的 ⟳ 是重新扫描，空曲库时也有「扫描本机音乐」按钮；「⋮ → 扫描指定文件夹」可只索引一个目录。扫描到一半想推倒重来，用「⋮ → 清空曲库（重置）」，清空后可以重新扫或按文件夹一个个加。
3. **整理成歌单**：「歌单」标签新建歌单 → 回「歌曲 / 专辑 / 文件夹」长按某首歌（或点专辑页、文件夹页的「加入歌单」）把歌收进去；播放时在队列页右上角「载入歌单」，整单按你的顺序播放。
4. **加网络服务器**：底部「来源」页**进入即自动扫描本机 Wi-Fi 子网**，「发现的设备」里会列出开着文件共享的电脑 / NAS 和 Jellyfin 媒体服务器；点右侧「添加」会带着 IP 跳到添加向导。**先填用户名和密码**（就是你在 NAS / Jellyfin 上设置的账号），再点「自动查找共享」选共享名，最后「测试连接」→「保存」。保存后进目录即可播放，或「加入曲库」整目录导入，之后在「搜索」页就能搜到服务器里的歌。也可以右上角 ＋ 手动添加 SMB / WebDAV / Jellyfin。
5. **连 Jellyfin**：右上角 ＋ →「添加 Jellyfin 媒体服务器」，填服务器 IP（如 `192.168.88.51`）、端口 `8096`（HTTPS 用 `8920`）、以及 Jellyfin 的登录账号密码 → 测试连接 → 保存。进去后按「媒体库 → 艺术家 / 专辑 → 歌曲」逐层浏览；点「加入曲库」可整库导入。歌曲按服务器上的原文件播放，不会被转成低音质。


> 扫描只覆盖**手机当前所在的子网**（页面会显示范围，例如 `192.168.1.0/24`）；如果服务器在另一个网段、连的是访客网络，或路由器开了 AP 隔离，扫描列表里不会出现，但**仍可手动填 IP 添加**。服务器地址可填局域网 IP（如 `192.168.1.10`）、`.local` 名称，或 NAS / 路由器的短主机名（如 `nas`）；短主机名会先解析并确认落在私网段才放行，公网地址一律拦截，这是刻意设计，不是故障。不需要账号的共享请勾「这台服务器不需要登录」。

---

## 3. 已真实跑通的验证（v2.0.8）

| 项目 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` | ✅ **15 个测试类 / 89 条用例，0 失败**（本轮新增文件夹路径归一化 10 条单测） |
| `./gradlew :app:lintDebug` | ✅ 0 error |
| `./gradlew :app:assembleRelease`（R8 混淆 + 资源压缩） | ✅ BUILD SUCCESSFUL；云端正式签名产物体积与 SHA-256 见 [Release v2.0.8](https://github.com/naboluoye/litelisten/releases/tag/v2.0.8) |
| `apksigner verify --verbose`（云端流水线） | ✅ 沿用同一把 RSA 4096 密钥，证书 SHA-256 `cc7b03e7…e7ac`，与 v1.0.0 一致，可覆盖升级 |
| `ci_guard.py --check all` / `--check apk` | ✅ PASS，合并后 Manifest 权限仍为 11 项主动申请 + 2 项已审计库注入（**未新增麦克风权限**） |
| GitHub Actions `CI` / `Release` | tag `v2.0.8` 触发签名发版，结果见仓库 Actions |

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
python3 scripts/bump_version.py 2.0.8     # versionCode 自动 +1
git add version.properties && git commit -m "release: 2.0.8"
git tag -a v2.0.8 -m "Release 2.0.8"
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
| 10 | 歌词只读同名文件 | 同名自动查找 + 共享目录同名查找 + 批量导入 + 粘贴导入 | Android 10+ 分区存储下非媒体文件常不可见，且多数人不会逐首下载歌词，必须有多条兜底 |
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
├─ docs/{qr-download.png, qr-download-card.png}  扫码下载图（随版本更新）
├─ scripts/{ci_guard.py, bump_version.py}      红线守卫 / 版本自增
├─ gradle/libs.versions.toml                   唯一依赖版本源
├─ version.properties                          versionCode=10 / versionName=2.0.8
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
- 歌词自动匹配在 Android 13+ 常受分区存储限制，此时请用「选择 .lrc 歌词文件」手动挂载、或「导入歌词文件夹」批量导入；两种方式都会存进本地数据库，重启不丢。
- 文件夹视图按歌曲路径归组：MediaStore 走「相对存储根的目录」，SAF 走「相对所选文件夹的路径」。若曲库里同时存在整机扫描与单独添加的同目录，同一首歌会出现两次，重置后只用一种方式整理即可。
- 歌单只存「歌曲 id + 顺序」，「清空曲库（重置）」会清掉歌单里的条目（歌单名字保留）；单首歌被移出曲库时，歌单里的残留条目会自动清理。
- 均衡器效果依赖设备/system 的 `android.media.audiofx` 实现，部分蓝牙设备或 ROM 可能不支持（页面会提示）。
- **HiFi 边界**：高保真输出解决的是 App 内部的降位与转码，最终音质仍受手机 DAC、系统混音、蓝牙编码（SBC/AAC/LDAC）限制；APE / DSD / WMA 无解码器，不支持。Jellyfin 自签名 HTTPS（8920）需要服务器使用受信任证书，局域网自签名证书不受支持。
- `connectedDebugAndroidTest` 仪器化测试与真机全流程回归矩阵仍未接入 CI。
