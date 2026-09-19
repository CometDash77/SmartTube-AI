# SmartTube 字幕翻译接入研究

研究日期：2026-09-19，Asia/Hong_Kong。状态：静态研究完成，应用实现及验收未执行。

## 结论与范围

kiss-translator 可借鉴按播放窗口预取、排队批量处理、提示词缓存指纹，以及取消请求与会话版本双重防护。不能照搬它的 AI 断句协议、轨道回退、浏览器渲染和缓存层。SmartTube 保留原字幕时间轴，独立编写协议、提示词和实现。

研究基线：kiss-translator `3d03f21c50536d67a5ca457e0181f8cd4911b781`，来源见[快照记录](kiss-translator-snapshot.md)；SmartTube `production` 的 HEAD 为 `1fbcc1a6cbb87740bbb9ae76d4ed90781ffe45a1`。实际共享目录是仓库内 `SharedModules/`、`MediaServiceCore/`，其提交分别为 `13f5687dd6757b02fbcdf14c5403d0339e377db5`、`9df453a0b13d593452b4817d778645916b978236`。未修改这些目录。

## 已核实的参考实现

### 1. 配置独立，但 API 和提示词资源共享

字幕设置读写 `subtitleSetting`，启动入口直接读取该分支，不通过网页文本规则选择配置；但使用全局 `transApis`、`prompts` 和语言变体设置。因此准确表述是“字幕配置独立”，不是“接口和提示词存储也完全独立”。证据：[src/hooks/Subtitle.js:8–15](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/hooks/Subtitle.js#L8-L15)、[src/subtitle/subtitle.js:44–80](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/subtitle.js#L44-L80)。

SmartTube 建议：规则、接口、单套表达指令分开保存，配置界面仍集中在“AI 字幕翻译”。不引入 kiss 的多接口选择和多提示词继承层。

### 2. 手工字幕和 ASR 有区分，但轨道回退不符合本项目要求

kiss 先用语言和 `kind` 匹配，ASR 使用 `kind === "asr"`；失败后可能选同语言其他轨、ASR、甚至列表最后一轨。这不能保证始终翻译用户选中的那一轨。证据：[src/subtitle/youtubeCaptionTracks.js:90–124](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/youtubeCaptionTracks.js#L90-L124)。

SmartTube 建议：用实际选中轨关联 `MediaSubtitle`；保留 `vssId`、原始语言代码、类型及内容标识，不能只按显示名称匹配。歧义时保留原文并提示，不静默换轨。同语言判断使用最终轨的语言元数据；未知语言不假定为目标语言，简繁中文等变体需明确策略。

### 3. 必须区分普通批量翻译和 AI 字幕断句

普通路径将逐条文本送到通用 `apiTranslate`，由批队列合并；队列按批内索引派发结果。另一条字幕 AI 路径让模型返回结束边界：程序合并一段事件文本，用首事件开始时间和末事件结束时间重建 cue。后者不是稳定 cue ID 的一对一翻译。证据：[src/subtitle/BilingualSubtitleManager.js:715–747](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L715-L747)、[src/libs/batchQueue.js:87–134](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/libs/batchQueue.js#L87-L134)、[src/subtitle/subtitleBoundaryProtocol.js:29–59](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/subtitleBoundaryProtocol.js#L29-L59)。

AI 路径还从可编辑提示词中的 `WEBVTT` 等字符串猜协议，遇非法边界只保留连续前缀，空译文可以通过边界映射。不能把它的“有索引”当成本项目所需的缺失、重复、乱序和空值校验。证据：[src/apis/trans.js:279–317](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L279-L317)、[src/apis/trans.js:343–362](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/trans.js#L343-L362)、[src/subtitle/subtitleBoundaryProtocol.js:46–58](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/subtitleBoundaryProtocol.js#L46-L58)。

SmartTube 建议：协议固定为请求 `items: [{id,text}]`、响应 `items: [{id,translation}]`；时间不发给模型。ID 在轨道快照内稳定，回写另携视频、轨道及配置版本。乱序按 ID 还原；未知 ID 丢弃；重复 ID 全部视为歧义；缺失或空值保留原文，其余有效结果保留。用户编辑区只影响表达，不决定 JSON 结构。

### 4. 批量与预取参数需要独立落实

kiss 默认预取 90 秒、节流 30 秒；字幕窗口逐条入队，通用队列按条数和字符数贪心装批并限制批次并发。它允许首条文本独自超过字符限额。证据：[src/subtitle/BilingualSubtitleManager.js:56–60](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L56-L60)、[src/subtitle/BilingualSubtitleManager.js:632–658](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L632-L658)、[src/libs/batchQueue.js:53–79](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/libs/batchQueue.js#L53-L79)。

SmartTube 保持用户指定值：向前 60 秒、每批最多 20 条/6,000 字符、并发 1。窗口包含当前仍在显示的 cue；字幕空档也继续预取。单条超过限额时首版保留原文并记录受控失败状态，不静默截断或重新断句。失败项要有重试状态，不能每次播放器 tick 都重新提交。

### 5. 取消机制有可借鉴点，也有作用域风险

manager 销毁时递增会话号、abort，并清理监听与字幕；译文回写检查会话号和 signal。provider 导航时清空状态并增加处理版本。值得借鉴物理取消和逻辑过期检查同时存在的设计。证据：[src/subtitle/BilingualSubtitleManager.js:99–121](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L99-L121)、[src/subtitle/BilingualSubtitleManager.js:679–703](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L679-L703)、[src/subtitle/YouTubeCaptionProvider.js:164–185](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/YouTubeCaptionProvider.js#L164-L185)。

反例：seek 处理器取消节流并重同步，没有直接更新翻译会话或 abort。源码中的 REVIEW 评论对闪烁的推断不能当成实测缺陷；实际回写还检查当前 cue 对象身份。能确定的是旧窗口请求不会仅因这些 seek 处理器而取消。证据：[src/subtitle/BilingualSubtitleManager.js:449–474](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L449-L474)、[src/subtitle/BilingualSubtitleManager.js:698–703](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L698-L703)。

另一风险：通用队列 key 不含视频或会话，批次调用只取第一项的 args，其中包括 signal。若来自不同会话的任务进同一批，取消归属可能混用；这是可执行代码支持的风险推导，未运行跨会话复现。证据：[src/apis/index.js:804–824](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L804-L824)、[src/libs/batchQueue.js:87–92](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/libs/batchQueue.js#L87-L92)。

SmartTube 建议：每个活动会话持有单队列、可取消网络 Call 和不可变配置快照；换视频/换轨/关闭/退出使会话失效，所有成功与失败回调都校验身份。seek 重排未发送任务，优先当前窗口；可取消无用在途请求以释放并发位。同轨同配置的已完成译文可保留缓存，但旧回调不能直接驱动新位置渲染。

### 6. 缓存不能只依赖接口名称

普通翻译缓存含文本、语言、接口 slug、提示词指纹等；所读指纹只包含提示词/语气/术语，未包含地址或模型。相同 slug 下修改地址或模型存在旧结果复用风险。这里仅指普通 `apiTranslate` 路径，不概括其他字幕缓存。证据：[src/apis/index.js:105–140](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L105-L140)、[src/apis/index.js:733–750](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/apis/index.js#L733-L750)。

底层使用浏览器 CacheStorage，不能直接搬到 Android，也不是用户要求的有界会话内存缓存。证据：[src/libs/cache.js:72–111](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/libs/cache.js#L72-L111)。

SmartTube 建议：有界 LRU，键含轨道/字幕内容指纹、目标语言、规范化地址、模型、提示词与协议版本；内存上限实施时同时约束条目与文本大小。配置版本用于并发防护，缓存键用于结果隔离，不能互相替代。密钥变更至少刷新会话并解除鉴权失败停机状态，密钥不写入可输出的缓存键。

### 7. 渲染回退不同

kiss 缺译文时创建省略号节点，仅译文模式只显示该节点；失败时会存入失败文字。因此不满足“失败或未完成仍显示原文”。证据：[src/subtitle/BilingualSubtitleManager.js:580–601](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L580-L601)、[src/subtitle/BilingualSubtitleManager.js:749–764](https://github.com/fishjar/kiss-translator/blob/3d03f21c50536d67a5ca457e0181f8cd4911b781/src/subtitle/BilingualSubtitleManager.js#L749-L764)。

SmartTube 应以播放器时间决定当前 cue，翻译到达只更新当前身份匹配的结果。双语和仅译文模式均在缺译文时显示原文；失败状态不作为译文缓存。

## SmartTube 接入与已有约束

| 接入位置 | 已有事实与实施建议 |
| --- | --- |
| [VideoLoaderController.java:268](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VideoLoaderController.java#L268) | 格式信息入口可交付视频标识及字幕列表。需在请求发起时捕获视频身份，防止旧格式响应绑定当前视频。 |
| [PlaybackPresenter.java:58](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/PlaybackPresenter.java#L58) | 已集中注册控制器，新增翻译控制器接收新视频、轨道、seek、释放等事件；同视频重新打开也应产生新会话。 |
| [ExoPlayerController.java:251](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/controller/ExoPlayerController.java#L251) | 手动选择通知紧跟选择请求；实际轨变化另有回调，必须覆盖自动恢复与关闭字幕。 |
| [YouTubeMPDBuilder.java:329](../../MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/formatbuilders/mpdbuilder/YouTubeMPDBuilder.java#L329) | manifest lang 优先用显示名，Representation ID 是生成编号，不能直接当语言代码或 vssId。准确映射是实施前必须解决的技术项。 |
| [SubtitleDecoderFactory.java:78](../../exoplayer-amzn-2.10.6/library/core/src/main/java/com/google/android/exoplayer2/text/SubtitleDecoderFactory.java#L78) | 原生工厂支持 VTT/TTML；复用解码结果的事件时间与 cue，不自行正则解析字幕。不修改子模块。 |
| [SubtitleManager.java:98](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/SubtitleManager.java#L98) | 当前跨回调去重会删除换行或重复文本。译文应在原字幕处理后组合，并统一写入 SubtitleView，切会话重置原文状态。 |
| [SubtitleSettingsPresenter.java:24](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java#L24) | 复用字幕设置对话框；全局设置与本视频临时开关分离。现有密码输入框支持遮蔽，但空提交被拦截，需独立清除入口。 |
| [OkHttpCommons.java:320](../../SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/okhttp/OkHttpCommons.java#L320) | DEBUG 网络配置添加 BODY logger，关闭 profiler 仍有 logger。复用 OkHttp 库时采用不带敏感日志的专用客户端，持有 Call 以取消，不修改共享模块。 |
| [BackupAndRestoreManager.java:184](../../common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/BackupAndRestoreManager.java#L184) | 应用备份按目录/文件名筛选，Manifest 还开启 Android 备份。独立私有密钥存储需同时排除应用导出和系统备份，兼容 API 17。 |

## DeepSeek 官方接口核验

2026-09-19 直接读取官方文档，确认默认地址 `https://api.deepseek.com` 和模型 `deepseek-flash` 当前有效。请求使用 `POST /chat/completions`，显式设置 `stream: false` 与 `thinking: {"type":"disabled"}`；JSON 输出模式还要求消息中明确要求 JSON。以上是文档核验，不是真实鉴权或模型调用验证。来源：[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion)、[Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing)。

程序固定协议与可编辑翻译指令分开组装；连接测试应验证一次最小非流式响应，后台执行。按用户约定，超时/429/服务端错误最多重试一次；401/403 停止当前会话并提示修正配置。不得依赖网络库的隐式重试叠加次数，也不能将响应正文、Authorization 或完整请求写入日志。

## Jev 使用与人工复核

第一轮通过项目技能执行 1 次 audit，5 个独立结论—证据关系；未额外做 rank，因为研究入口已明确，不凑足候选数量。输入、路径、行号、原文和完整结果见 [audit 输入](evidence/kiss-translator-3d03f21c-jev-audit-input.json)、[audit 结果](evidence/kiss-translator-3d03f21c-jev-audit-result.json)。

服务成功，无失败重试；批次第一项 args、destroy 双重防护两项达到采用阈值；seek 被判证据不足，渲染和缓存未达阈值，均回到人工源码复核。对 seek 额外核对同步链路与当前 cue 检查，对缓存补读实际 cacheOpts。未把结果当成完成/安全认证，也未声称节省阅读量。最初一轮 SmartTube 检查的 1 次 rank/1 次 audit 仅见此前对话，本轮没有伪造其持久化原始输入。

### 用户要求的第二轮 Jev 核验

增加调用方与被调用方证据后，对 9 项研究结论执行 1 次新的 audit；没有重发第一轮相同输入。状态大小 19,584 UTF-8 字节，低于技能 20,000 字节上限。服务成功，无服务失败或相同输入重试。

| 项目 | Jev 结果 | 处理 |
| --- | --- | --- |
| 独立字幕配置、共享 API/提示词 | supports 0.82，confidence 0.73 | 未达 winner 0.85 阈值；人工核对 hook 与启动参数后保留范围受限的结论 |
| 轨道 fallback | supports 1.00 | 达阈值，人工核对源码 |
| 边界协议会合并事件时间轴 | supports 0.99 | 达阈值，人工核对源码 |
| 提示词内容决定协议 | supports 0.98 | 达阈值，人工核对源码 |
| 非法边界后停止接收 | supports 0.99 | 达阈值，人工核对源码 |
| seek 无直接 abort、回写检查当前 cue | supports 0.98 | 达阈值；不扩大为已经复现闪烁 |
| 普通缓存未直接包含地址/模型 | supports 0.96 | 达阈值；不泛化到所有缓存路径 |
| 批次仅向执行函数传首项 args | supports 0.91 | 达阈值；跨会话风险仍待运行验证 |
| 仅译文缺结果显示占位符 | supports 1.00 | 达阈值，人工核对源码 |

完整原始输入及结果：[第二轮输入](evidence/kiss-translator-3d03f21c-jev-audit-2-input.json)、[第二轮结果](evidence/kiss-translator-3d03f21c-jev-audit-2-result.json)。所有结论均保留源文件和行号。两轮合计 2 次实际服务请求；第二轮未改变核心建议，也未证明节省时间或验证功能完成。

## 验证矩阵与剩余验收

以下是待实施测试，不是已通过结果：

- 原生解析：手工 TTML/VTT、ASR 滚动/重复文本、空档、重叠 cue、非零起点、末尾字幕；保持时间轴，不能默认任意重叠字幕的结束时间单调递增。
- 协议：正常/乱序/缺失/重复/未知 ID、空译文、坏 JSON、截断内容，验证有效结果保留和原文回退。
- 批次：20 条、6,000 字符边界，超长单条，窗口空档，seek 后优先级，以及网络实际并发不超过 1。
- 生命周期：A 视频延迟成功/失败回调晚于 B 视频；A→B→A、同视频重开、换轨、改配置、关闭、退出和快进快退；旧结果不能渲染新会话。
- 配置/缓存：提示词恢复默认、地址/模型/语言/提示词/轨道隔离、容量淘汰、密钥变更恢复请求；导出与日志无密钥。
- 错误：超时、429、5xx 总共最多两次尝试，取消不重试；鉴权失败停止后续批次并去重提示。
- 后续按项目 build 技能执行受影响测试及 stbeta 编译；发布候选执行 lintStbetaRelease、assembleStbetaRelease。遥控器焦点、长提示词输入、双语布局、连续播放和快速拖动另行设备验收。

本轮完成静态源码研究、官方文档核对和文档/证据检查。未执行第三方项目测试、Android 构建、设备验证或真实 DeepSeek 请求；未更改应用代码、子模块、分支或执行远程推送。精确轨道映射与解码 cue 到稳定 ID 的实测是实施阶段优先项，不需要新增设计审批关卡。
