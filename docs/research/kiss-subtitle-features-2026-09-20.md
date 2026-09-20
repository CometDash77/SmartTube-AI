# Kiss Translator 四项字幕功能研究

日期：2026-09-20，Asia/Hong_Kong。状态：静态研究完成，实施方案待用户确认；未修改应用代码。

## 结论

可以在 nightly-24 已打通的工作流上增量加入四项能力。复用现有来源绑定、原生解码、固定 Chat Completions 协议、串行预取、字幕单一写入点和本地导出。最需要设计的接缝是**断句后显示与翻译共用同一派生时间轴**；只加提示词或只合并请求都不足以实现 Kiss 的断句体验。

| 用户所指能力 | Kiss 的实际含义 | SmartTube 建议 |
| --- | --- | --- |
| 智能上下文 | 接口级历史轮次；字幕另有 AI 视频摘要增强 | 先修全时间轴邻文，再加同会话已验证译例；可选一次视频摘要/术语分析 |
| 规则断句 | 本地规则合并/重新分段，并建立新的字幕起止边界 | 原始时间轴不可变；建立有来源映射的派生句子时间轴，按实际已有事件边界合并 |
| 强制重翻 | AI 断句服务与翻译服务不同，草稿译文交给翻译服务重做 | 单服务架构采用明确动作：清当前轨译文并从播放位置重新翻译；注明与上游不同 |
| 字幕加载通知 | 可关闭的播放器短提示，涵盖等待、处理、加载成败 | 依据已接受事件提示“原字幕已就绪”“当前片段译文已就绪”，不抢遥控器焦点 |

详细实施、参数初值及验收见[实施计划](../plans/kiss-subtitle-features-implementation-plan.md)。这些是设计建议，不是已完成产品行为，也不能据静态分析保证翻译精度提升。

## 研究基线与证据

- SmartTube：`production` / `91982d5e`；应用成功基线为 `164508024b6611327f6349295a967b5385ebb4c0` / nightly-24。456 项通过、CI 与电视成功为[已有记录](../plans/subtitle-current-status.md)，本轮没有重跑。
- Kiss：固定 `3d03f21c50536d67a5ca457e0181f8cd4911b781`，复用[上次快照](kiss-translator-snapshot.md)，不声称是最新上游。此次重新检查 ZIP SHA-256，507 个文件与 ZIP 对应条目逐字节一致；没有安装依赖或运行第三方代码。
- `settings.gradle` 实际选择仓库内 `SharedModules/`、`MediaServiceCore/`，两个 sibling 均不存在；未改变子模块。
- 下列上游链接均指固定提交；本地文件路径以仓库根为基准，行号对应上述 SmartTube HEAD。Jev 所需原文另存为[输入证据](evidence/kiss-subtitle-features-2026-09-20-jev-input.json)。

## 1. 智能上下文有两条独立路径

### 1.1 历史翻译轮次

接口默认 `useContext=false`、`contextSize=3`；后者是消息条数上限，标准 `addPair` 按完整 user/assistant 对取整，不能解释成“默认三轮”。`getMsgHistory` 的全局 Map 按 `apiSlug` 区分；`handleTranslate` 读取历史并把 `hisMsgs` 送到请求生成器。标准 pair 写入拒绝空 assistant 正文，保留 role/content，并成对淘汰；部分服务仍有不同历史写入分支，不能把 pair 保证推广至所有服务。

证据：[默认参数](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/config/api.js#L1548-L1549)、[容量常量](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/config/api.js#L14)、[历史容器](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/history.js#L26-L120)、[请求接线](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L2001-L2041)。

字幕重新处理会清当前服务历史，切接口还会清前一个服务，但历史容器自身不是视频隔离容器。SmartTube 应按视频、轨道、配置和连续播放段隔离，只吸纳协议校验通过的翻译，seek 清历史；不能直接复制全局 apiSlug 队列。

### 1.2 视频摘要增强

字幕配置 `aiContextSlug` 默认 `-`（关闭），可单独选择 AI 接口。启动字幕处理前，provider 将展平原文合并后取前 8,000 个 JavaScript 字符；不足 200 字符跳过。将标题、简介与该段字幕送去生成主题、术语、专名、语气等背景，结果写入 `docInfo.summary`。这是视频开头样本摘要，不是全文理解或真实发言人识别。

调用者 `await` 分析后再进入断句/manager 流程；异常被捕获，可继续字幕处理。写回检查 videoId 与 processingVersion，但摘要 API 不接收 signal，不能将“旧结果被丢弃”说成“摘要请求已物理取消”。

证据：[配置与选择 UI](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/views/Options/Subtitle.js#L975-L992)、[采样/防旧回写](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L855-L898)、[启动顺序](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L607-L634)、[摘要请求与解析](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L2508-L2577)。

摘要缓存仅用 `apiSlug + videoId`，没有完整模型/字幕内容版本。普通翻译缓存的 `ctx` 仅取摘要前 50 字符。摘要进入实际提示词还取决于模板是否包含 `{{summary}}`：系统模板和非批量用户模板有替换；批量用户 JSON 本身只添加 title/description 等，不自动追加 summary。不能仅看到 `docInfo.summary` 非空就声称最终 HTTP 一定带了摘要。

证据：[摘要缓存](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L1104-L1133)、[译文缓存](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L739-L748)、[系统占位符](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L126-L145)、[用户请求模板](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L159-L190)。

### 1.3 当前项目可复用与缺口

`SubtitleBatchPlanner.java:24–31,82–86,126–152` 已有前 3/后 2 条原文上下文、每方向 500 code points、全批 6,000 上限。它从**当前播放窗口**收集邻文，因此已经播完的前文可能完全取不到，且 before 按近到远排列；同一稳定 ID 在连续帧复用时还需去重。应先修这些确定性问题，再评估额外 AI 上下文的收益。

`SubtitleRequestBuilder.java:30–50,59–85`、`SubtitleTranslationConfig.java:48–53` 当前没有历史译例、视频摘要字段或相应配置身份。新增字段必须经真实 `SubtitleTranslationRequest.create` → client → HTTP body 验证；沿用第四轮发现“builder 单测通过但生产没调用”的教训。

## 2. 规则断句不是 AI 断句，也不是屏幕自动换行

`useAlgorithmBreaker="rule"` 为默认；另有 statistical 模式，不能把 `sentenceBreaker.js` 名字当成规则实现。规则主实现是 `youtubeSubtitleProcessing.js` 的 `processSubtitles` / `formatSubtitles`，`runBuiltinSegmentation` 选择路径。

- 空格语言：检测句末标点、超过 1,000ms 的停顿、达到 10s 的合并时长，以及在指定条件下达到 15 个词。长句进行第二次处理，可在逗号/英文连接词前拆分。默认配置长句阈值 100；函数缺省值 120，UI 允许 20–300，不能混称同一默认。
- 中文等分支：句末标点（含引号/括号尾缀）、超过 1,000ms 的停顿或约 30 字符触发落句。上游 NO_SPACE_LANGUAGES 还包含韩语等，移植时不能据此删除这些语言原有空格。
- 输出文本由片段拼接，start 取首片段开始、end 取末片段结束。阈值是启发式，不是所有异常输入都严格受限；单个长片段未必可再分。
- 上游预处理还可按词长比例展开粗粒度 ASR 的时间。这是估算，不是音频词级对齐；SmartTube 首版不采用此估时法。

证据：[规则合并及边界](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeSubtitleProcessing.js#L269-L385)、[语言分支和长句二次处理](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeSubtitleProcessing.js#L397-L488)、[规则/统计路由](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeSubtitleProcessing.js#L522-L562)、[粗粒度估时](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeSubtitleProcessing.js#L54-L72)、[应用默认配置](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/config/setting.js#L179-L186)。

上游存在停顿、长句、韩语空格、音乐标记和缺少词级偏移的回归案例，本轮只读测试源码，未运行。它们不能替代 SmartTube 对 native decoder 帧、清屏事件、双行、多 speaker、seek 与导出的测试。

本项目 `SubtitleTimelineBuilder.java:39–75` 已保留清屏帧和稳定 itemId；`SubtitleManager.java:88–105` 的原文来自实时 native onCues，而 binder `:248–268` 以快照 itemId 取译文。若只改后者的时间轴，将出现“多个原条目合为一句译文，却套在一个 native 槽位”的结构风险。必须把原文和译文显示作为同一帧选择，仍经 `renderCurrent` 唯一写到 SubtitleView。

## 3. 强制重翻的语义边界

设置 `forceSubtitleRetranslate` 默认 false。开启 AI 断句且断句 apiSlug 与翻译 apiSlug 不同时，断句返回的现有译文被标 `_isDraftTranslation=true`，文本暂存；manager 预取时将草稿视为需翻译，收到有效结果后替换并清标记。关闭时沿用已有断句译文，无译文的条目仍会正常补翻。同服务设置不构成这一替换路径。

切换此设置会取消字幕处理、增加 processingVersion、销毁 manager、清历史并重处理原始事件。**没有证明该开关绕过普通翻译缓存**：manager 调 `apiTranslate` 没传 `useCache=false`，后者仍有缓存命中返回。

证据：[草稿标记](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeAiSegmentation.js#L64-L68)、[服务条件](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeAiSegmentation.js#L294-L306)、[草稿调度和替换](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L651-L696)、[普通 API 调用](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L715-L739)、[缓存命中](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L752-L767)、[重新处理](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L812-L843)。

SmartTube 没有独立 AI 断句模型或断句草稿，不建议为复制一个开关引入第二套供应商。计划中的“强制重翻当前字幕”明确是本项目产品适配：清当前来源的成功缓存、失败预算与翻译历史；取消旧请求并建立新翻译代次；复用原始字幕，从当前 60s 窗口开始，之后按播放推进。

`SubtitleTranslationCache.clear()` 单独不够：`SubtitleBatchPlanner` 的 done/pending/oversize、dispatcher 的 in-flight/cooldown，以及 display 原译文要协调。手动重翻不能绕过 429 等待或无 Key/鉴权停止状态。

## 4. 字幕加载通知的实际触发

Kiss 提示包含找不到字幕/等待字幕、同语言、AI 处理、AI 上下文分析、重新处理、加载成功和失败。`youtubePlayerUi.showNotification` 默认 2 秒，复用一个通知元素，每次清旧 timer；设置为 false 时立即隐藏。字幕设置 UI 的 `showLoadNotification=true` 是回退值，不能据 root 默认配置缺此键就判为关闭。

`#startManager` 创建并启动 BilingualSubtitleManager 后立即发“加载成功”。流式 AI 首块到达也可启动 manager；因此其通知不保证全片译文完成。

证据：[显示和开关](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubePlayerUi.js#L233-L247)、[设置回退值](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/views/Options/Subtitle.js#L486)、[首块启动](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L785-L801)、[加载成功位置](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L1018-L1020)。

SmartTube 已有 `onAiTimelineSettled(accepted, installed)`、翻译 observer、批结果回调、白名单诊断事件和菜单状态，可以从这里产生通知。不得从菜单重画/一秒 tick 发通知；过期来源结果不得显示错误或成功。AI 关闭仍可提示原始字幕加载状态，但不得显示正在翻译。提示文案与是否有译文、是否仅部分完成相符。

## 5. Jev 辅助核验与人工裁决

实际执行一次 live audit，6 项，模型 `typesafe/jev-1.13-20260917`；6,612 输入 / 259 输出 tokens。4 项达到项目阈值，2 项人工回源；无失败、回退或重试。不使用 rank：符号入口已明确，没有八个真正模糊的候选。

| ID | Jev 结果 | 人工结论及影响 |
| --- | --- | --- |
| force-semantics | supports .72，confidence .58，需复核 | 回看 apiTranslate 缓存分支与 manager 入参，支持草稿替换，但不支持“必定跳过缓存”；产品适配显式注明 |
| summary-cancel | supports .93，confidence .90 | 签名和调用均未带 signal；本项目摘要必须接取消和身份检查 |
| notification-meaning | supports .95，confidence .93 | 通知触发仅证明 manager 已启动；改用分阶段真实状态文案 |
| local-context-window | supports .98，confidence .97 | 另查 SubtitleTimeline.framesInWindow:71–83 确认排除已结束帧；改全时间轴索引查邻文 |
| segmentation-display | supports .81，confidence .72，需复核 | 回源确认 native 原文与 binder 时间轴分开取值；例如两个 native 槽位合并成一个 segment 后不存在逐槽对应保证。是设计反例，未声称设备已复现 |
| retranslate-reset | supports .87，confidence .79 | done/pending 和旧请求会影响重新排队；任务必须同时处理缓存、规划和请求代次 |

原始资料：[输入](evidence/kiss-subtitle-features-2026-09-20-jev-input.json)、[实际输出](evidence/kiss-subtitle-features-2026-09-20-jev-result.json)。全部问题只含相关公开/项目源码；未发送用户字幕、Key 或原始设备日志。Jev 不证明代码正确、效果提升或实施完成。

## 6. 未确定事项与停止扩展范围

- 新的精度故障样例尚未提供；当前可以确定接线与回退设计，不能指定真实人称/术语错误已被修复。
- native 解码后的 ASR 细粒度边界与 Kiss json3 不同；规则首版以实际已有边界为限。无法可靠拆分的长条目保留，设备验收再决定是否值得扩展解析器。
- 不引入 AI 时间戳生成、统计断句库、双服务草稿工作流、全文自动翻译或 Codex compaction。上述机制不属于完成这次四项适配的必要条件。
- 用户已经明确本轮只研究和计划；后续实施须以用户确认的范围为准，不把这份报告作为自动执行或发布授权。
