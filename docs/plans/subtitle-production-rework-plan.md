# 字幕与 AI 翻译生产链重改计划

> **当前状态已更新（2026-09-20）**：用户确认 nightly-24 工作流打通，下一阶段为精度改善，见[当前状态](subtitle-current-status.md)。下文保留 nightly-21 失败时的规划与后续澄清，不再代表当前验收状态，也不授权自动执行全量重改。

> **用户澄清后的优先级更正**：本次“失败”具体是点击保存 API Key 后，无论填写什么都显示保存失败。先修复 `SubtitleKeyCipher` 的外部 IV 与 AndroidKeyStore 随机加密策略冲突，以及 Keystore 不可用时未进入内存回退；修复提交 `4a83f633` 已通过完整 CI（447 tests），已发布 nightly-22；电视验证尚待完成。下文“失败操作未知”是规划时的历史信息，现已被此澄清替代，不能继续调查成字幕导出失败。整体重改范围保留。

日期：2026-09-20（Asia/Hong_Kong）。状态：**方案已编写，重改尚未实施，设备验收未通过**。

本文件是当前重改入口，取代第三轮计划作为下一步执行安排。旧计划、CI 结果、设备原始日志保留历史身份，不删除、不改写成成功证据。本轮范围是解释当前实现、制定重改计划、纠正文档与进度记录；没有修改应用、安装电视、发布版本或调用真实翻译服务。

## 1. 结论与证据边界

- 用户在提供 nightly-21 验收候选信息后报告“显示失败”、质疑字幕获取实现、无法理解翻译功能。当前产品验收不能记为通过，也不能继续把增加提示文字作为主要修复方案。
- 指定 APK：`stbeta-32.53-nightly-21-21-debug`，源码 `fdd3ea233ff9b9a56077950cea243885656c63d2`。本轮工作树 HEAD `1c2eb06dbf4133a9de4c9dc0b67e7182af610072`，与其差异只有文档；应用源码可用于解释该候选。未重新下载 APK 或查询 CI，446 条通过及签名信息引用已有记录，不能算本轮实测。
- **最新“失败”的具体按钮、完整提示、播放源和诊断文件尚未提供**，因此尚不能判定为绑定失败、下载失败、解析失败、写文件失败或翻译失败。旧 nightly-18 的 `sourceBound=false` 不能证明 nightly-21 同一根因，也不能证明用户没有选轨。
- 已证实的设计问题：`SubtitleAiMenuState.of()` 在有 Key、来源绑定、就绪且不暂停时直接返回 `TRANSLATING`；没有要求时间轴成功或真实请求在途。R3 修复了 AI 关闭的文案，但开启后的状态仍可能误导。
- 已证实的交付问题：用户指南仍把第三轮已经改过的 Key 标签描述成旧标签；交付索引、验收矩阵、日志顶部与日志末尾阶段不同。文档不能继续各自宣称“当前”。

## 2. 当前 APK 到底如何实现

以下路径均相对仓库根目录。`common/src/main/java/com/liskovsoft/smartyoutubetv2/common/` 简写为 `common-java/`；行号对应上述源码基线。

| 环节 | 当前真实行为 | 关键位置与局限 |
| --- | --- | --- |
| 原生播放 | 播放器按自己的轨道选择、加载和解码流程显示字幕 | AI 不负责语音识别；ASR 轨是 YouTube 提供的字幕，不是本应用听音生成 |
| 建立来源映射 | DASH/SABR 清单里的文本 Format 与服务返回的字幕列表按完整 base URL 唯一匹配 | `common-java/exoplayer/ExoMediaSourceFactory.java:188,205,327`；只证明这些路径建立映射，不能推断每一种播放源都覆盖 |
| 找当前轨 | 从 TrackSelectorManager 读取选中 MediaTrack，再用 Format **对象身份**查绑定表 | `common-java/exoplayer/controller/ExoPlayerController.java:291`；`common-java/exoplayer/other/SubtitleSourceBinder.java:40,78,133`；无映射/多义时拒绝，不能按语言挑第一条 |
| 触发快照 | 播放、换源、轨道变化等事件进入 Presenter，刷新会话后请求协调器 | `common-java/app/presenters/PlaybackPresenter.java:469,1132`；选轨请求与实际生效通知不是同一事件，真实时序尚需覆盖 |
| 再取字幕 | 使用播放器的 DataSource，但**重新打开选中字幕 URL**；后台读取并用原生解码器构造不可变时间轴 | `common-java/exoplayer/controller/ExoPlayerController.java:314`；`common-java/exoplayer/other/SubtitleSnapshotFetcher.java:66`；不是直接导出屏幕上已出现的文字 |
| 安装时间轴 | 协调器检查请求身份，丢弃旧请求结果，当前结果安装到 binder | `common-java/exoplayer/other/SubtitleTimelineCoordinator.java`；`AiSubtitleSessionBinder.java:170`；有原生字幕不等于有此时间轴 |
| 导出原文 | 点击时固定来源、时间轴、缓存快照，在后台生成 ZIP 并写入存储 | `SubtitleExportController`、`SubtitleExportSnapshot`、`SubtitleExportBundle`；没有时间轴就不能导出完整原文，与 Key/AI 开关无关 |
| AI 翻译 | 开启后按播放位置预取时间轴条目；带 id 的文本批次经配置服务的 Chat Completions 接口翻译，返回结果校验后按 id 缓存并叠加 | `SubtitleBatchPlanner`、`SubtitleTranslationDispatcher`、`SubtitleTranslationService`、`SubtitleFrameTranslations`、`SubtitleComposer`；不是翻译音频，也不是自动寻找网上另一套字幕 |
| Key 和连接测试 | 菜单调用密码输入框保存 Key；手动连接测试发送小型合成文本，独立于当前字幕 | `common-java/app/models/playback/controllers/PlayerUIController.java:432`；`PlaybackPresenter.java:591`；存在源码入口不代表电视遥控器输入已验收 |

因此现状有两条并行数据路径：**原生字幕显示**与**额外时间轴获取→导出/翻译**。第二条还依赖来源绑定、身份同步和事件时序。此前测试以 fake host/人工构造 Format 为主的成功结果，不能证明真实播放器跨过这些边界；这属于验证范围缺口，不等于已证实每一条路径都错误。

## 3. 重改后的产品契约

1. 原文功能先独立成立：无 Key、AI 关闭也能发现可用字幕、清楚选择来源、准备并导出。没有字幕时直说没有，不要求配置 AI。
2. 用户选择的是当前视频的明确字幕轨。默认可沿用播放器已生效轨；未选轨时提供可选来源入口，而不是让用户盲等。人工字幕、平台 ASR、平台翻译轨要能区分，禁止默选同语言第一条。
3. “发现”“选中”“生效”“来源绑定”“时间轴可用”“翻译请求在途”“已有译文”是不同事实。屏幕显示、导出和翻译使用同一个来源身份与可解释状态。
4. AI 是可选的文本翻译步骤：选字幕→确认原文可用→设置目标语言和 Key→主动开启。失败保留原文；缺译文不伪装成翻译完成。
5. 不承诺无字幕视频也能翻译，不引入语音识别/OCR/外部搜字幕服务；这些属于独立需求。
6. 输入 Key、取消、保存、清除都应在遥控器上可达；不让用户通过开启翻译来寻找输入框。占位值测试必须关闭 AI，不发送网络请求。

## 4. 目标结构与实施顺序

保留经验证的纯函数、限额、取消隔离、导出快照和单一渲染出口；重做真实播放器与这些模块之间的接入和状态所有权。不要为“重写”并行保留两套下载器或两套翻译管线。

### W0 — 固定失败基线和观测边界

- 影响：当前状态索引、诊断模型、选轨/绑定/获取/导出接入点。
- 记录最新失败的具体操作和提示；有新日志时按 APK/SHA/会话关联，原始文件只读保留。没有设备材料时仍可完成源码事件图、回放夹具和诊断设计。
- 建立一条操作对应一条结果的复现记录：初始条件、操作、预期、实际、失败阶段、是否出现原文。不得再将多份累积计数相加。
- 验收：记录允许明确回答“在哪个阶段停止”，否则只标 UNKNOWN；不把旧失败分类套用新版本。

### W1 — 重做字幕来源接入，先通原文

- 影响：`ExoMediaSourceFactory`、`SubtitleManifestAdapter`、`SubtitleSourceBinder`、`TrackSelectorManager`、`ExoPlayerController`、`PlaybackFragment`、`PlaybackPresenter`。
- 从实际生效的播放器轨道事件创建一个原子来源快照：视频代次、媒体源代次、轨道身份、绑定状态、支持的读取方式。Presenter 不再通过多次 getter 拼接可能不同代次的 source/format/key。
- 源身份在清单构造时确定并随实际轨道传播；优先沿用可靠的 manifest/representation 关联，Format 对象引用只作为快速路径。若需替代引用匹配，必须证明复合身份在人工、ASR、平台翻译和重复语言轨中唯一；不能退化成语言/名称猜测。
- 先用真实 parser→TrackGroup→选择事件夹具证实引用是否保留、选择管理器何时刷新，才决定身份映射改动。覆盖 DASH/SABR；其它路径明确为支持、无字幕或不支持，禁止沉默失联。
- 验收：选轨、同轨重选、关闭、开启、切视频、清单重建、延迟生效均产生正确快照；有原生文本但无绑定时能明确指出接入阶段，不能归咎用户未选轨。

### W2 — 原文时间轴服务脱离 AI 会话

- 依赖 W1；影响 `SubtitleTimelineCoordinator`、`SubtitleSnapshotFetcher/Reader`、`AiSubtitleSessionBinder`、导出控制器。
- 原文时间轴由一个字幕源会话持有，AI binder 只消费，不再成为无 Key 原文功能的结构性前提。导出和翻译读取同一不可变时间轴及来源代次。
- 全量读取只对已确认可读的完整文本源启用，复用播放器网络配置并有字节、时限、取消和去重上限；对分段/动态字幕明确单独能力，未实现时不谎称可完整导出。
- 下载与原生显示的时间基必须用 fixture 对齐：偏移、空帧、滚动 ASR、重复行、seek、非零起点。仅收集当前屏幕 cue 不能冒充完整字幕。
- 区分网络打开、HTTP、读取、大小限制、解码、不支持格式、空时间轴、过期丢弃；用户明确重试只重试此来源一次，禁止轮询或无限重试。
- 验收：无 Key/AI 关闭的真实来源样本能生成非空 `original.srt`；连续导出不重复抓取；过期成功/失败/finally 不污染新来源；下载失败不破坏原生播放。

### W3 — 用事实驱动状态与诊断

- 依赖 W1/W2；影响 `SubtitleAiMenuState`、诊断报告/事件环、Presenter、菜单资源。
- 来源状态：未选、等待轨道生效、无法绑定、不支持、读取中、可用、读取失败。翻译状态独立：关闭、缺 Key、等待原文、请求中、部分可用、当前窗口完成、限流、鉴权失败、服务失败。
- `请求中` 只能由真实 dispatch/settle 驱动，不能从“条件齐备”推导；同语种跳过、没有待译条目、返回无有效译文均有真实状态。AI 关闭永远不显示翻译中。
- 一次操作生成本地不透明 operationId；事件带 sessionGeneration、stage、固定 reason、单调时间。保留选择请求与生效、绑定、读取开始/结束、安装/丢弃、导出开始/结束、翻译开始/结束的因果顺序。
- 进程统计与会话统计分开命名；环缓冲区记录截断计数及覆盖范围；截图里的错误码能对应诊断里的操作。兼容旧 formatVersion，新增格式有解析/白名单回归。
- 默认诊断不含 Key、Authorization、URL 查询、字幕正文、原始异常/响应体；错误码从白名单映射，不能把“详细日志”实现为直接打印服务返回。日志导出独立于字幕来源可用性。
- 验收：每个终态都有可测试转换及一项可执行下一步；没有永远“准备中/翻译中”的悬挂状态；诊断导出在字幕故障时仍成功。

### W4 — 重接翻译与电视交互

- 依赖 W2/W3；影响 `SubtitleTranslationDispatcher/Service`、`SubtitlePrefetchLoop`、`SubtitleComposer`、`PlayerUIController`、`SimpleEditDialog`、settings store。
- 先确认原文，后开启翻译；Key 与语言配置可独立进入。请求仅消耗当前来源/配置代次的条目；关 AI、换视频、换轨、改配置后旧响应不得显示。
- 沿用单请求并发、有限重试、429 暂停、鉴权停止、缓存及单一渲染出口；对其真实接入而不是只对纯函数验证。部分返回保留原文，不重复发送已完成项。
- 离线假服务覆盖 200/部分结果/空结果/401/403/402/429/5xx/超时/坏 JSON/超长响应，连通调度→请求→缓存→状态→画面，不请求用户真实 Key。
- 遥控器验收输入一个字符再取消、占位值保存/清除、重开菜单、焦点返回、低 API 会话存储回执；没有设备时只记自动证据，不写“输入问题已修复”。
- 真实调用只在用户自行配置 Key 并明确授权后测试一个最小批次，记录成功条目数和状态，不收集 Key 或正文。

### W5 — 集成门禁、稳定交付与文档收口

- 依赖 W1–W4；应用变化按 `smarttube-build` 技能在 GitHub Actions 运行 wrapper：受影响 `exoplayer.other` 与完整 common 单测、两模块 `lintStbetaRelease`、候选组装与签名/元数据/哈希检查。沿用 CI 单测 JDK 11、lint/build JDK 17 的既有配置，实施时重新核对。
- 加入穿过真实 parser/selector/Presenter 接入的测试，不能仅增加 fake host 自洽用例。优先现有框架；每个已重现缺陷要有修改前失败、修改后通过的证据。
- 设备顺序：原生字幕→无 Key 原文导出→Key 输入取消/保存/清除→离线翻译接入→获准最小真实翻译→切轨/seek/关开/长播放→存储失败和旧设备。每项记录同一个 APK 身份，缺证据就是未验。
- 项目稳定证书、包名、versionCode 和覆盖升级单独验证。debug 包不得原样晋升正式版；不再让反复卸载成为默认调试路径。签名缺失不阻塞源码与自动测试。
- 明确回退：源码可回退至已记录提交；设备回退受签名/versionCode/数据约束，禁止承诺无损或擅自卸载。新接入失败时保留原生字幕与诊断，AI 默认关闭。
- 完成标准：原文链和可操作输入先过真机，翻译链有端到端证据，诊断能解释失败，升级身份可持续；CI 通过仅证明自动门禁。没有这些证据不得称“可生产使用”。

## 5. 文档与日志治理

- 当前入口：[当前状态](subtitle-current-status.md)，只维护候选、验收结论、证据出处和一个下一步，不复制全部历史。
- 本文件负责目标和工作包；[用户指南](../ai-subtitle-user-guide.md)只解释真实行为及待验边界；[历史交付记录](evidence/subtitle-ai-rc-delivery-2026-09-20.md)保留各候选事实，不冒充最新入口。
- [旧实施矩阵](evidence/subtitle-implementation-status.md)、[第三轮计划](subtitle-round3-implementation-plan.md)、[原主计划](deepseek-subtitle-dsh-ptc-plan.md)顶部明确历史/被替代范围；原始日志与旧判断保留，错误通过有日期的更正说明处理。
- 每次变更只在当天开发记录追加里程碑，并更新当前状态；记录源码 SHA、自动检查、设备检查、未决问题四类证据，不把实施数量/测试数量累计当成功率。
- 本轮不改原始设备日志，不远程改 GitHub release 描述；“生产端日志重整”中的运行时实现属于 W3，不应被本轮文档修改冒充已完成。

## 6. 本轮源码复核与执行入口

Jev live audit 1 次/3 项，原输入与输出见 [input](evidence/subtitle-rework-jev-input.json)、[result](evidence/subtitle-rework-jev-result.json)。前两项 supports 达阈值；额外读取项 supports 0.71/confidence 0.56，人工补查 `ExoPlayerController.java:314–316` 的新 DataSource 流与 Fetcher 的 open/read 路径后确认。结论只解释实现与设计风险，不定位本次设备根因，不认证验收。

下一步从 W0/W1 开始实现并验证真实来源接入。最新失败的按钮、原文提示及对应诊断若可取得，用于收敛复现；其缺失不阻塞原子来源快照、状态修正和真实接入测试。不得再以“用户选轨后重试”作为整个修复结论。
