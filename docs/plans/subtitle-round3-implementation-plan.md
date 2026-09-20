# 第三轮字幕修复实施计划（交下一位 agent）

状态：**R1–R4 已实施（2026-09-20，本地代码与静态/单元验证），R5/R6 待 CI 与设备**。2026-09-20，Asia/Hong_Kong；复核基线 `production` / `3e6a0d24`。
原计划由用户转交的下一位 agent 实施；实施内容、本地验证与未决项见当日开发记录 `docs/development/2026-09-20.md` 的 13:45 HKT 条目。本文件仍是验收门槛定义，不是发布授权：CI 通过或生成 APK 都不等于设备验收通过。
证据与裁决：[独立复核](evidence/subtitle-round2-independent-review-2026-09-20.md)。本计划细化并取代主计划 §20 的待审建议；§14/§15 的产品与交付契约继续有效。

## 1. 更新后的目标与优先级

一次修复候选同时解决：**Key 入口可发现并可实际输入、AI 状态诚实、原文导出失败可操作、诊断能识别请求边界**。
用户已明确从未输入 Key；不可把本次失败归咎于用户输错 Key，也不可要求先配置 Key 才修复原文导出。

| 阶段 | 产出 | 完成条件 |
| --- | --- | --- |
| R1 / P0 | 同一时刻的选轨、绑定与抓取观测 | 无轨、未知来源、播放器未就绪和去重可区分；不引入字幕/凭据内容 |
| R2 / P0 | 点击时快照驱动的导出提示 | 不再对无轨/永久失败建议盲等；换视频后不会解释成新视频状态 |
| R3 / P0 | API Key 入口、输入/保存反馈、AI_OFF 修正 | 用户能完成输入操作；自动检查及设备验证分别记录 |
| R4 / P0 | 选轨触发与身份同步回归 | 能证明有效选轨会准备原文，无 Key/AI 时无翻译请求；失败才修触发链 |
| R5 | CI 与单一修复候选 | 同一 SHA 的必需门禁与资产记录；无已知缺陷不必要地拆成两轮候选 |
| R6 | 设备第三轮与稳定升级 | 产品交互验收与项目签名/升级分开记账；不以 CI 绿替代 |

实施顺序 R1 → R2 → R3 → R4 → R5 → R6。R3 可与 R1/R2 独立开发，但整合后才发验收候选。
原 P1-1 提升为本轮必修；P2 并入 R2。签名配置不作为 R1–R4 的阻塞项。

## 2. R1：复用现有模型补齐观测

涉及：`AiSubtitleHost`、TV `PlaybackFragment`、`ExoPlayerController`、`PlaybackPresenter`、`SubtitleTimelineCoordinator`、`SubtitleExportSnapshot`、`SubtitleDiagnosticReport` 及相关现有测试。
Java common 根为 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/`；多数模型位于 `exoplayer/other/`。

1. 在 UI 线程取得当前真实字幕格式、解析来源和解析后的 binder status。先 resolve 再读 status；不能把 `FormatItem != null` 当作选中有效轨。沿既有 host → fragment → controller 路径传递，不跨层强转 TV 类。
2. 在既有 snapshot 的来源观测中保存 `subtitlesSelected`、`sourceStatus`；复用 `SubtitleSourceBinder.Status` 的五个值。另以一个有界 `playerReadiness` 枚举区分 `READY/NO_HOST/NO_DISPLAY/NO_BINDER`，防止缺播放器被当作 NOT_SELECTED。非 READY 时 selected=false 仅为占位，提示优先处理 readiness，不声称确实未选轨。
3. 协调器 `request()` 用小型结果枚举替换模糊 boolean：`STARTED/ALREADY_READY/REUSED/IN_FLIGHT/NO_SOURCE/NO_FORMAT/NO_FACTORY/NO_IDENTITY`。NO_IDENTITY 覆盖缺 sourceKey 或 locator，不记录它们的值；在 scheduler.begin 前区分原因，不把 null begin 全解释为在飞。
4. Presenter 补齐 coordinator 外的 readiness 拒绝，固定映射到请求事件。统计口径：`timelineRequests` 仅实际 STARTED；`timelineInstalls` 仅当前请求成功安装（含复用安装）；`timelineSkips` 仅拒绝前置条件，不将去重/reuse 混成错误。另保留 `lastTimelineRequestResult`，避免 40 条环覆盖后丢失最近原因。
5. 计数采用与现有 Presenter stats 一致的生命周期（本轮选进程内 Presenter 生命周期），报告文档明确其不等于当前视频计数；每次 SOURCE_CHANGED 将 last result 重置为 NOT_REQUESTED。过期回调不得改当前 last result/安装计数；不从有限事件环倒算计数。
6. SUBTITLES_ON/OFF 只在实际可见性状态变化时记录，不在 tick 或重复菜单绘制时刷屏。选轨与字幕可见性是不同概念，不把这个事件当作字幕源已绑定的证明。
7. 新报告 `formatVersion=2`，旧字段保留。全部新增值为固定枚举、布尔或有界计数；禁止日志打印 sourceKey、locator、URL、Key、异常文本及字幕正文。复用既有隐私/白名单测试与 40 条环测试，不扩大日志容量。

验收：每种拒绝和重用结果均可用 fake host/worker/main 队列稳定重现；重复请求不重抓，旧回调不会串源。缺 host/display 的 UI 观测有直接测试或明确设备待验项，不能在捕获诊断时启动下载。

## 3. R2：导出失败以点击时快照为准

涉及：`SubtitleExportController`、`SubtitleExportSnapshot`、`PlayerUIController`、三套 strings 与现有导出测试。

复用 `NO_TIMELINE` 兼容既有事件，增加一个有界失败原因到 `ExportResult`（或等价的固定子码），由点击时 snapshot 计算。
UI 根据返回原因显示消息，不再在结果回调中查询 presenter 当前 snapshotStatus/sourceStatus。
已有可导出时间轴按既有 bundle 契约处理；下表只用于 NO_TIMELINE，不额外强制 AI/Key 条件。

| 点击时证据 | 用户提示方向 |
| --- | --- |
| playerReadiness 非 READY | 播放器/字幕功能尚未就绪；返回播放重试，仍失败可导出诊断 |
| NOT_SELECTED，且真实有效轨未选 | 长按字幕键，选择一条文本字幕，再导出原文；无需 API Key 或 AI |
| SOURCE_UNKNOWN / SOURCE_AMBIGUOUS | 这条字幕来源暂不支持识别；换一条字幕或导出诊断 |
| UNBOUND 或 selected/status 冲突 | 来源尚未绑定；重新选择字幕，仍失败导出诊断，不归咎于未选轨 |
| BOUND 且请求在飞 | 正在准备原文，请稍后重试 |
| BOUND 但明确失败终态/未发起/无身份 | 给出对应恢复动作；不声称一定会自动完成，不循环自动重试 |

保持导出按钮及诊断按钮可达；让同一原因映射服务失败反馈，无需再建按钮禁用/自动选轨状态机。
测试需覆盖：无轨不调用 writer、不输出空 ZIP；UNKNOWN/AMBIGUOUS；BOUND 在飞与失败终态；点击 A 后切换 B，A 的失败仍解释 A；AI 关且无 Key 的已就绪原文成功导出。

## 4. R3：真正可用的 Key 输入与状态

涉及：`PlayerUIController`、`SimpleEditDialog`（仅必要的小重载）、`SubtitleAiMenuState`、`common/src/main/res/{values,values-zh,values-zh-rTW}/strings.xml`。

1. AI 区靠前显示“API Key：未配置 / 已配置，输入或替换”，复用已有状态字符串。服务地址可配置，因此不要无条件写死“DeepSeek Key”；默认服务说明可以提 DeepSeek。
2. 密码对话框有明确 API Key 标题及独立用途提示；若需要不同 hint，给现有 `showPassword` 增加小重载，旧签名保持行为兼容。输入不回填旧 Key，保留密码掩码，说明“用于向当前服务认证”，不要声称 Key 永不发送网络。
3. 使用现有 settings store；持久保存成功显示“API Key 已保存”，会话存储显示现有“仅保留到关闭”反馈，不同时承诺持久保存。失败保留对话框并提示；取消不改配置，清除后能看到未配置状态。重开菜单必须读到新状态；不要求为即时刷新重建整个设置中心。
4. `AI_OFF` 显式映射“AI 翻译未开启（本视频）”；菜单的 sourceBound 参数使用 R1 真正绑定结果。Key 未配置/鉴权停止的现有优先级保持明确。字幕准备信息另用简短说明表达，AI_OFF 不应被“未选轨”或“翻译中”覆盖。
5. 两条简短引导区分用途：“导出原文：先选文本字幕，无需 Key”；“AI 翻译：选字幕 → 配置 API Key → 开启翻译”。配置和测试连接在 AI 关闭时仍可进入；手动连接测试不要求正在播放的字幕轨。
6. 必须检查实际输入链：方向键进入 EditText，键盘/可用输入法能输入字符，确定/取消焦点可达，提交回调写入当前 store。自动验证可使用离线占位文本且绝不点击连接测试；仅看到对话框或源码有 showPassword 不算设备验证。

测试：优先扩展现有 settings/menu 测试及实际 UI 资源映射测试，避免只测 `Status.AI_OFF` 却遗漏资源映射；有可用 Robolectric 配置则验证 dialog 输入/确认/取消，不新增框架。TV 上必须实测输入一个字符再取消，以及保存/清除回执；真实 Key 由用户自行输入，agent 不索取、记录或截图明文。

## 5. R4：先证实选轨触发，再作最小修复

基线：`ExoPlayerController.selectFormat` 先 `selectTrack` 再发 `onTrackSelected`，Presenter 此回调只转发；`onTracksChanged` 才产生 `onTrackChanged` 并刷新绑定、请求时间轴。
不能预设前一回调已有最终生效轨，也不能凭日志缺请求就认定此处漏触发。

1. 用可控队列/现有 player 测试缝模拟：初始 CC 关→选文本轨、选同轨、切轨、字幕开关、host 延迟就绪、换源后格式与 controller 身份不同步；核对真实选轨刷新与抓取先后。
2. 断言 host 已解析 source 与 controller sourceKey 同步后才 STARTED；NO_IDENTITY 能暴露不同步，且下一次有效事件能恢复。保留 N1 同源去重、失败可显式重试和过期成功/失败/finally 不污染当前请求的测试。
3. 仅在重现漏触发时修复相关字幕路径：刷新已生效 source 身份后调用既有 coordinator；用相同测试证明改变有效。不为音频/视频选择新增抓取，不引入轮询、盲目延时或第二下载器。
4. 未重现则交付观测修复与已跑测试，将具体设备时序保留为待验，不能写“N1 无回归已证明”。

## 6. R5/R6：验证与交付门槛

开始实施前重新读取当日日志、Git 状态、嵌套 AGENTS 及实际共享 checkout；不要把本次基线当作之后仍未变化。
构建遵循 `.agents/skills/smarttube-build/SKILL.md`：GitHub Actions 上用 wrapper，当前 CI 单测为 JDK 11（Robolectric 兼容），lint/build 为 JDK 17。本地做静态/文档检查，不另跑 Gradle。

| 自动检查 | 要求 |
| --- | --- |
| `:common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"` | 受影响模型/协调器/导出/菜单测试 |
| `:common:testStbetaDebugUnitTest` | 现有完整 common 必需门禁，不恢复 continue-on-error |
| `:common:lintStbetaRelease :smarttubetv:lintStbetaRelease` | 两模块 lint；记录实际规则加载异常 |
| 现有 CI build task | 开发候选 `assembleStbetaDebug`；项目签名 RC 按 release_mode 使用 `assembleStbetaRelease` |
| APK 与文档 | 现有 apksigner/元数据/哈希资产；docs checker、diff 检查 |

记录最终 SHA/run/tag/测试结果/资产哈希；按最终实现做适用的 Jev audit 并人工裁决，不能复用本轮旧源码判断来认证新代码。
在获准的下一轮实施/CI 交付范围内推进；本轮只提供计划，未运行 CI、提交或发布。

设备最短验收顺序（固定同一修复候选）：

1. 无 Key、AI 关闭、未选字幕：状态显示未开启，导出提示选轨，诊断能区分 NOT_SELECTED 与未就绪。
2. 选一条可绑定文本字幕（先用历史成功的 ASR 类型），屏幕出现原文；无需 Key 即导出，确认非空条目及匹配来源；诊断为 BOUND，STARTED/安装统计一致。
3. 打开 API Key 入口，以遥控器实际输入字符后取消；确认无配置变化。使用本地占位值作保存/清除验证时不启动 AI 或连接测试，不产生外发请求。
4. 用户自行配置真实 Key 且明确同意最小真实调用后，再手动连接测试及 AI 翻译。真实 API 调用未获授权时，只挂起此项。
5. 快速切轨/换视频/字幕开关；旧导出结果不混入新视频。另选不可识别轨（可取得时），确认有原生字幕但不可绑定的提示和诊断；没有真实样本时保留 fake 证据与设备缺口。
6. 检查译文/双语/部分失败、存储边界、长播放和低 API 等主计划 N6–N9 未完成项。未做不算过。

签名：复用既有 `release_mode=true` 通道。仅检查所需 Secrets 名称是否存在，不读取/打印值；四个名字为 `SIGNING_KEY`、`KEY_STORE_PASSWORD`、`ALIAS`、`KEY_PASSWORD`。
稳定签名 RC/覆盖升级验收需要实际匹配证书、包名和版本策略；debug 候选不能冒充可持续升级版本。
缺签名只阻塞相应出口；单次安装的功能诊断可以继续，但不默认卸载现有应用、不宣称数据可保留。

## 7. 可直接交给下一位 agent 的任务文本

> 请按 `docs/plans/subtitle-round3-implementation-plan.md` 的 R1–R4 实施本轮修复，并按 R5/R6 记录实际验证与待验项。先读最新开发记录和工作树状态。用户确认从未输入过 Key，而且没有找到可输入的位置；不要只改文案就宣称已修复输入问题。原文导出独立于 Key/AI。保留 N1 身份隔离与已有导出契约，以点击时快照解释失败，补齐固定枚举诊断，修正菜单把格式对象当绑定的错误。选轨触发只有重现后才改，禁止盲目轮询或重复抓取。优先复用既有模型/测试/对话框，按项目要求用 Jev 辅助最终语义审核。完成已授权的修改及相关验证，记录源码、CI 与设备证据的区别；签名、设备、真实服务未满足时只挂起对应项。不要索取或记录明文 Key，不擅自卸载电视应用，不把生成 APK 等同于验收通过。当前文档阶段未实施任何应用修复。
