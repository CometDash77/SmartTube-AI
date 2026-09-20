# T13 电视本地一键导出字幕与诊断日志 —— 阶段进度报告

日期：2026-09-20（Asia/Hong_Kong）· 分支：`production` · 交付形态：GitHub prerelease 测试候选（debug 回退签名）+ 本地源码与文档
本报告记录**当前进度**与**已经做过什么**，并明确区分"已验证"、"未验证"和"待决策"。

---

## 1. 一句话状态

功能已实现并在真机上通过了核心路径验收：**在 TCL / Android 11 电视上，未开启 AI、未配置 Key 的情况下，从自动字幕（ASR）轨成功导出原文 ZIP（520 条，1040 帧时间轴）**；远端 GitHub 已完成测试 / lint / 组装 / 签名校验 / prerelease 全流程；剩余项为译文形态验收、权限与空间等边界提示、签名身份与若干体验细节。

## 2. 交付物（用户视角）

### 2.1 两个按钮在哪里

长按遥控器字幕键 → 现有字幕菜单 → 底部 AI 区域最后两项（始终可见，不在开发者开关后）：

- **「导出字幕」** / Export subtitles
- **「导出诊断日志」** / Export diagnostic log

接线位置：`common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/PlayerUIController.java`（按钮 / 启动与重复点击提示 / 结果与失败反馈）。

### 2.2 文件保存在哪里

公共目录 **`Documents/SmartTube/Exports/`**，文件名带时间戳，绝不覆盖同名文件：

- `SmartTube-subtitles-YYYYMMDD-HHmmss.zip`
- `SmartTube-diagnostics-YYYYMMDD-HHmmss.txt`

Android 10+ 复用现有 `MediaStoreFile`（与备份导出同一路径，无需存储权限）；旧版本直写公共目录并复用项目存储权限检查。写入后**回读校验**，只有可完整读回才算成功；空间不足、权限拒绝、同名冲突、写入失败都有稳定错误码与可操作提示。

### 2.3 导出内容

字幕 ZIP（`SubtitleExportBundle`）：

| 条目 | 内容 |
| --- | --- |
| `original.srt` | 原文，覆盖整条视频已取得的时间轴 |
| `translated.srt` | 仅译文；缺失处回退原文，任何一条都不为空（有译文时才生成） |
| `bilingual.srt` | 原文在上、译文在下；缺失处只有原文（有译文时才生成） |
| `untranslated.srt` | 只含**仍没有译文**的 cue，可播放（有缺失时才生成） |
| `translation-status.txt` | 逐条状态 `TRANSLATED` / `FAILED` / `NOT_ATTEMPTED` + 时间码 + 原文一行 + 汇总（含导出时 AI/Key/目标语言/快照状态） |
| `README.txt` | 覆盖说明（中英双语要点：缺失回退、缓存上限可能淘汰、末条结束时间为估算、不需要 AI 开关或 Key） |

诊断报告（`SubtitleDiagnosticReport`）采用 **35 项字段白名单**：应用/Android/设备版本、来源类型与声明 MIME、快照状态、时间轴条数、缓存与批次计数、显示模式、有限事件码；**不含** Key、Authorization、Cookie、账号、签名 URL、HTTP 正文、字幕正文、logcat、偏好设置；仅本地保存，不上传。

### 2.4 与 AI 的关系（关键设计）

- **导出与 AI 翻译完全解耦**：不需要打开 AI 开关、不需要 Key。
- 时间轴由字幕事件在后台准备（视频加载 / 换轨 / 媒体源替换；seek 仅在尚无时间轴时重试；同一源已有时不重复读取）。
- 导出按钮本身**不发起抓取、不调用 DeepSeek**、不改缓存上限。
- 关闭 AI 开关**不会**丢弃原文时间轴（这正是第一次设备失败的原因）。
- 原文与译文共用同一条时间轴，导出时按点击时刻的会话快照固定；换视频/换轨不会混入。

## 3. 本次实现清单（源码）

新增（`common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/`）：

| 类 | 职责 |
| --- | --- |
| `SubtitleExportSnapshot` | 点击时刻的不可变会话副本（来源类型/MIME/语言/vssId、会话状态、计数、时间轴引用、译文与状态映射、事件码） |
| `SubtitleSrtFormatter` | SRT 生成（CRLF、HH:MM:SS,mmm、清屏帧与零长帧跳过、末条结束时间估算、原文/仅译文/双语/仅未译四种输出、覆盖统计） |
| `SubtitleExportBundle` | ZIP 组装与条件化条目、状态文件、README、无时间轴时拒绝 |
| `SubtitleDiagnosticReport` | 字段白名单报告（每值单行、80 字符上限、等号转写） |
| `SubtitleExportEventLog` | 40 条事件环，写入即收敛为 [A-Z0-9_] |
| `SubtitleExportWriteOutcome` | 写入结果与稳定错误码 |
| `SubtitleExportController` | 后台单任务编排（点击时取快照、忙碌守卫、UI 线程回送、异常兜底） |
| `SubtitleExportFileStore` | 公共目录写入与回读校验（API 29+ / 旧版本两条路径） |
| `SubtitleDiagnosticEnvironment` | 应用/Android/设备信息读取 |

改动：`SubtitleTranslationCache`（并发保护 + `snapshot()` + `statusSnapshot()`）、`AiSubtitleSessionBinder`（`getTimeline()`、`getTimelineOfCurrentSource()` 按源归属、源变化时取消在飞批次）、`PlaybackPresenter`（时间轴与 AI 解耦、点击时快照、事件记录、主线程回送）、`PlayerUIController`（两个按钮、结果对话框、失败原因、权限用途说明）、三套字符串资源（values / values-zh / values-zh-rTW）。

测试：5 个导出相关测试类 + 2 个既有测试类被本次改动影响并修复（见 §5）。

## 4. 远端交付管线（GitHub）

工作流 `.github/workflows/CI.yml`（名称：Build, test and publish stbeta test APK；`master` / `production` push + 手动 dispatch）：

| Job | 内容 |
| --- | --- |
| tests（JDK 11） | **门禁**：`:common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"`；另跑整模块套件作为**带标注的基线**（既有 2 条 ScreensaverManagerTest 失败，不作为门禁） |
| publish（JDK 17） | 两模块 lint → 组装 APK → `apksigner verify` 校验 → 写 SHA-256 清单 → 发布唯一 tag prerelease |

- 缺 4 个签名 Secrets 时**不发布未签名的 release 包**，改为构建 `stbetaDebug` 并以醒目说明标注"debug 签名回退"（不生成临时密钥掩盖）。
- VirusTotal 上传默认只在手动 dispatch 且存在 Key 时运行。
- `.gitignore` 增加 keystore.properties / key.jks / *.jks / *.keystore。

已发布的候选（同一分支每次 push 都会发一个）：

| tag | 目标提交 | 说明 |
| --- | --- | --- |
| `stbeta-32.53-nightly-2-2-debug` | `a0a1ae60` | 首个候选（解耦前），已用于第一次设备验收（失败） |
| `stbeta-32.53-nightly-3…6` | 中间若干提交 | 每次 push 各出一个；均为解耦前代码，**不用于验收** |
| `stbeta-32.53-nightly-9-9-debug` | `e3512c73` | **当前验收版本**（解耦 + 按源归属修复） |

已修的自身缺陷：① 首版"缺签名即整任务失败"改为明确的 debug 回退；② release 说明里代码围栏用双引号包裹反引号被 bash 当命令替换，导致元数据段为空（已修）；③ 时间轴按源归属的修复（见 §5.3）。

## 5. 设备验收过程（含一次失败与重新设计）

### 5.1 第一次验收（候选 -2）：导出字幕失败

用户回传两份诊断报告（TCL / Android 11，ASR 轨）。日志显示：`AI_ENABLED` → `TIMELINE_REQUESTED` → **1.766 秒后 `AI_DISABLED`** → `SNAPSHOT_CANCELLED`，于是 `snapshotStatus=CANCELLED`、`timelineFrames=0`，导出按设计以 `NO_TIMELINE` 拒绝共 15 次，未产生空文件（诊断导出在同一会话中两次成功）。

结论：**不是写文件失败，而是"时间轴抓取只由 AI 开关触发 + 关闭开关取消了在途抓取"**；同时失败提示"请开启字幕后等待首次快照"与实际机制不符。

### 5.2 按用户要求重新设计

用户判定"先开 AI 准备时间轴再导出"不合理，要求**导出与 AI 完全解耦**，且原文、仅译文、原文+译文、被中断的原文+译文、翻译失败的字幕都要能导出。据此改动：

- 时间轴改由字幕事件在后台准备；关闭 AI 不再取消在途快照；已有时间轴不重复抓取。
- ZIP 增加 `untranslated.srt` 与 `translation-status.txt`（逐条状态），README 说明"不需要 AI 开关或 Key"。
- `NO_TIMELINE` 提示改为直接显示最近一次快照状态并说明真实机制（三种语言）。

### 5.3 第二次构建失败与修复

解耦版首次提交的 CI 门禁失败（4 条）：2 条 `AiSubtitleSessionBinderTest` + 2 条 `SubtitleSeekCancellationTest`。根因是我把"源变化即清空时间轴"放进 `refreshSource()`，而既有契约要求 `refreshSource` 不得销毁已安装的时间轴（时间轴可以先安装；显示字幕恢复时要用缓存帧重画）。修复改为**按源归属**：安装时间轴时记录源 key，新增 `getTimelineOfCurrentSource()`，导出与"是否已有时间轴"判断都只在该 key 与当前选中源一致时成立；显示与预取路径行为不变。

### 5.4 第三次验收（候选 -9）：通过

用户回传的两份产物已随本报告入库：`docs/plans/evidence/device-logs/2026-09-20/`

| 产物 | 字节 | SHA-256 |
| --- | --- | --- |
| SmartTube-diagnostics-20260920-113003.txt | 1,535 | `5ace2bbc40d388590b178b69081cd7b039401974f878e20d2591784220416a44` |
| SmartTube-subtitles-20260920-113006.zip | 45,748 | `567409e68c97fc61b03fbc9ebdc1b38540ceb253faec281cc086ba98aec6e866` |

核对结论：

- 诊断：`appVersionName=32.53-nightly-9`、`aiEnabled=false`、`keyConfigured=false`、`snapshotStatus=OK`、`timelineFrames=1040`、`timelineItems=520`、缓存与批次计数全 0。
- ZIP 条目：`original.srt` 39,657 B、`untranslated.srt` 39,657 B、`translation-status.txt` 43,210 B、`README.txt` 1,653 B；无 `translated.srt`/`bilingual.srt`（无译文时的正确条件行为）。
- 状态文件：520 条数据行全部 `NOT_ATTEMPTED`；汇总 translated=0 / failed=0 / notAttempted=520 / coveragePercent=0；记录导出时 AI 关闭、无 Key、快照 OK。
- SRT：序号与 `00:00:00,000 --> 00:00:01,870` 格式正确；全部未译时 `untranslated.srt` 与 `original.srt` **逐字节相同**（符合预期）。

## 6. 验证矩阵（三层）

| 项目 | 本地（源码级） | GitHub Actions | 真机 |
| --- | --- | --- | --- |
| 编译 | 11 个类 JDK 17 javac exit 0；Presenter/UI 仅剩未生成的 R 字段诊断 | release debug 组装成功 | —— |
| 单元测试 | 导出相关 6 个类 JUnitCore 通过（含 `48 tests` 的导出集合） | 门禁范围 BUILD SUCCESSFUL（JDK 11，含此前失败的 4 条） | —— |
| 兼容性 lint | 未本地执行 | 两模块 lint BUILD SUCCESSFUL（JDK 17） | —— |
| APK 与签名 | 下载产物用本机 apksigner 复核 Verifies（v1+v2） | apksigner verify 全部 Verifies，证书与清单记录在案 | 安装成功 |
| 导出原文（AI 关闭 / 无 Key / ASR） | ZIP 结构、状态文件、SRT 由单元测试覆盖 | —— | **通过**（本次，520 条） |
| 导出诊断日志 | 白名单与不泄漏由单元测试覆盖 | —— | **通过**（两次） |
| 译文形态（仅译文 / 双语 / FAILED） | 单元测试覆盖 | —— | **未验证**（设备缺方便的 Key 输入入口） |
| 权限拒绝 / 空间不足提示 | 代码路径与错误码存在 | —— | **未验证** |
| 遥控器焦点 / 播放不中断 | —— | —— | **未验证**（用户未报告异常） |
| 项目签名身份 | —— | 缺 4 个 Secrets，发布的是 debug 回退 | 装的是 debug 签名包 |

## 7. 已消解的既有风险

- **ASR（自动字幕）载荷能否取到（计划 §13.4 未决）**：本次设备证据表明走应用内数据源成功取回并解码（1040 帧 / 520 条，`text/vtt`），不再是"未证明"。
- **导出会生成伪成功空文件**：无时间轴时明确拒绝，设备日志共 15 次拒绝均未产生文件。

## 8. 未决与剩余工作

1. **重复抓取（新发现，非正确性）**：设备事件日志为 `TIMELINE_REQUESTED` ×3 + `SNAPSHOT_OK` ×3，同一视频字幕被下载解码三次。建议改为"同一源 key 只保留一次在飞请求"。改动小、风险低。
2. **译文形态验收**：需要 API Key，而设备上缺少方便的输入入口（T08/T09 范围）。修好入口后才能验收 `translated.srt`/`bilingual.srt`/FAILED` 覆盖率。
3. **边界与体验**：存储权限拒绝、空间不足的实际提示；遥控器焦点；导出期间播放不中断（用户未报告问题，但未专门验证）。
4. **签名身份**：仓库无 Secrets，候选为 debug 签名；若电视上已装其它签名的同包名应用会拒绝覆盖。需要 4 个 Secrets 才能产出项目签名的 release 候选（且不要未备份就卸载）。
5. **versionCode 未调整**（2443）：只保证可覆盖同版本号构建；不承诺覆盖更高版本号或不同签名。
6. **批次级失败原因**未逐条保留（只有"尝试过无结果"与"从未到达"两种区分），若需按 401/429 分类导出需额外记录。

## 9. 关键提交与运行索引

| 类型 | 标识 | 内容 |
| --- | --- | --- |
| commit | `dc481477` | T13 源码 + 测试 + 文档 + CI（28 文件） |
| commit | `a0a1ae60` | CI 工作流（测试/lint/构建/签名/prerelease）+ .gitignore |
| commit | `af4eddca` | 修复 release 说明的代码围栏引号 |
| commit | `83443fde` | 与 AI 解耦 + 全部翻译状态 |
| commit | `e3512c73` | 时间轴按源归属（修复 4 条 CI 测试） |
| commit | `45052a31 / f20967c8 / 89975971` | 验收记录与字幕包入库范围调整 |
| run | `35483822239` | 解耦前候选构建成功（tag -2） |
| run | `35485647998` | 解耦版首次构建**失败**（4 条测试），已由修复取代 |
| run | `35485905205` | 解耦 + 修复版构建成功（tag -9，本次验收版本） |

## 10. 大白话版（给非工程读者）

1. 现在电视上长按字幕键，菜单最下面有两个新按钮：一个导出字幕文件，一个导出诊断报告。文件放在电视公共目录 `文档/SmartTube/Exports/`，用电视上的文件管理器就能看到、复制到 U 盘。
2. 导出的压缩包里是标准字幕文件：原文一份、有翻译时再给"只有译文"和"原文+译文"各一份、还没翻到的部分单独一份，外加一份"每条字幕翻没翻出来"的清单和说明。没有翻译或翻译到一半就中断，也能照常导出，包里的清单会如实写明。
3. 诊断报告是给排查问题用的：只记录版本、设备、字幕来源、数量、状态码这类信息，**不会**记录你的 Key、账号、网址或字幕内容，也不会自动上传。
4. 这次你在电视上实测通过了：**不打开 AI 翻译、不填 Key**，也能把一部没有原字幕的视频（用自动字幕）的原文完整导出（520 条）。
