# Kiss 字幕功能实施状态（2026-09-20）

日期：2026-09-20，Asia/Hong_Kong。分支 `production`，起点 `91982d5e` 加前一会话未提交的 K0–K2 工作树。本文件是**实施状态与证据矩阵**，不是进度日志；当天进度见 [2026-09-20 记录](../../development/2026-09-20.md)，需求与任务卡见 [K0–K7 增量计划](../kiss-subtitle-features-implementation-plan.md)，审查发现见[复核与骨架](../kiss-agent-review-and-skeleton-2026-09-20.md)。

**一句话状态：** 复核发现的四项 K1/K2 接线缺口已修复并有生产接缝回归；智能上下文（三档 + 一次性摘要）已实现并接入生产请求；强制重翻已实现；**规则断句的算法与显示/导出链路尚未实施**（设置项存在但无行为）。代码已提交（`6c70e370`）并推送，GitHub Actions run 35510576265 通过并发布验收候选 **`stbeta-32.53-nightly-25-25-debug`**（debug 回退签名）；仍未安装设备、未做真实付费调用、未发布正式 release。

## 1. 本文件对应的改动范围

| 类别 | 内容 |
| --- | --- |
| 修复（复核 P1/P2） | 配置变更统一内容事务；连接测试与预取共享单一 AI 槽位；通知交付时重读开关并核对来源/代次身份；译文通知改由真实"接受并显示"入口产生 |
| 新增生产类（9） | `SubtitleLoadNoticeDelivery`、`SubtitleTextPair`、`SubtitleSummary`、`SubtitleContext`、`SubtitleContextSource`、`SubtitleSessionContext`、`SubtitleSummaryParser`、`SubtitleSummaryAnalyzer`、`SubtitleSummarySession` |
| 修改生产类 | `SubtitleTranslationDispatcher`（内容代次、共享槽位、会话上下文回填、档位）、`AiSubtitleSessionBinder`（内容事务、显示事件、会话上下文、重翻事务）、`SubtitleBatch`/`SubtitleBatchPlanner`（去重索引、全时间轴邻文、上下文预算）、`SubtitleRequestBuilder`/`SubtitleTranslationRequest`/`SubtitleProtocolInstruction`/`SubtitleTranslationService`（档位 payload 与摘要请求）、`SubtitleLoadNotificationPolicy`（删 4 行转发）、`PlaybackPresenter`、`PlayerUIController`、三套 `strings.xml` |
| 新增测试类（7） | `SubtitleLoadNoticeDeliveryTest` 8、`SubtitleSessionContextTest` 8、`SubtitleSummaryParserTest` 5、`SubtitleSummaryAnalyzerTest` 9、`SubtitleSummarySessionTest` 4、`SubtitleContextPayloadTest` 4、`SubtitleRetranslationTest` 4 |
| 扩充测试 | `SubtitleTranslationDispatcherTest` +3、`AiSubtitleSessionBinderTest` +1、`SubtitleSeekCancellationTest` +1、`SubtitleBatchPlannerTest` +2 |

## 2. 复核四项发现的处置

| 发现 | 处置 | 回归证据（生产接缝） |
| --- | --- | --- |
| P1 配置变更绕过取消入口，旧译文可污染新配置缓存 | `AiSubtitleSessionBinder.onConfigurationChanged()` 成为唯一事务：先移 translation generation，再 `dispatcher.invalidateContent()`（内容代次 +1 且 cancel），再清缓存；dispatcher 在写 cache/显示前校验内容代次 | `SubtitleSeekCancellationTest.aLateAnswerAfterAContentConfigurationChangeIsDropped`、`SubtitleTranslationDispatcherTest.aContentInvalidationDropsTheInFlightAnswerAndFreesTheSlot` |
| P1 连接测试互斥只有一个方向 | dispatcher 增加共享 AI 槽位：`tryAcquireExternalSlot`/`releaseExternalSlot`，`isBusy()` 与 `tick()` 都读取；测试先占槽、被占即 BUSY；槽位由终态回调释放（取消不提前放槽） | `SubtitleTranslationDispatcherTest.theConnectionTestAndThePrefetchShareOneSlotInBothOrders`、`aLateExternalReleaseCannotDisturbANewInFlightBatch` |
| P2 通知用入队时的旧状态与不完整身份 | `SubtitleLoadNoticeDelivery` 在交付时重读通知/AI 开关并核对入队时捕获的来源 key 与 translation generation；`revoke()` 在关闭开关与 release 时清待显示并隐藏当前提示 | `SubtitleLoadNoticeDeliveryTest`（8 条：开关、代次、来源、AI 关闭、撤销、分离 UI） |
| P2 未来译文真正显示时没有通知入口 | `AiSubtitleSessionBinder` 新增 `TranslationDisplayListener`，在真正写入非空译文的入口触发；仅含未来条目的成功不提示，其帧进入画面时提示一次 | `AiSubtitleSessionBinderTest.aCachedFrameReachedLaterNotifiesExactlyOnceThroughThePolicy` |
| Ponytail：删无用转发 | `SubtitleLoadNotificationPolicy` 删除只做赋值的私有方法 | 编译 + 全范围测试 |

## 3. 四项功能的当前状态

| 功能 | 状态 | 证据 | 仍缺 |
| --- | --- | --- | --- |
| 字幕加载通知 | **自动验证通过** | `SubtitleLoadNotificationPolicyTest`、`SubtitleLoadNoticeDeliveryTest`、内容事务与显示事件回归 | 遥控器焦点/遮挡与真机文案；关闭开关后的实际提示消失行为 |
| 智能上下文（三档） | **连通，档位真正影响请求** | `SubtitleContextPayloadTest`（基础不含译例/摘要、连贯含译例、增强含摘要、空数据不产生空字段）、`SubtitleBatchPlannerTest.neighboursComeFromTheWholeTimelineInTimeOrder`、`SubtitleSessionContextTest`（8 条） | 上下文"准确率提升"；真实样本对照；设备首译延迟 |
| 视频增强一次性摘要 | **实现并接线（compile + 单测）** | `SubtitleSummaryParserTest`、`SubtitleSummaryAnalyzerTest`（9 条：单次、上限、超时、迟到、失败不重试）、`SubtitleSummarySessionTest`（4 条） | 真实服务下的 5s 预算表现；摘要质量的设备评估 |
| 强制重翻 | **实现并接线（compile + 单测）** | `SubtitleRetranslationTest`（4 条：成功项也重新请求、旧代次迟到被丢弃、摘要保留而译例清空、AI 关/无来源拒绝） | presenter 结果枚举与菜单回执的真机确认；连续点击与 429 的实际手感 |
| 规则断句 | **未实施（仅设置项与身份已落地）** | 三档/开关的存储与 namespace 自 K1 起存在；无 segmenter、无派生时间轴、无派生显示/导出 | 整个 K4/K5 链路（算法、稳定来源映射、局部回退、原子显示、派生导出） |

## 4. 已执行的验证（本地，非 CI）

| 命令 | 结果 |
| --- | --- |
| `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"`（JDK 11） | **BUILD SUCCESSFUL, exit 0；66 suites / 519 tests / 0 failures / 0 errors**（K0–K2 基线 470 → 修复后 483 → K3 后 515 → K6 后 519）；最后一次运行在行尾归一化后的**最终字节**上进行 |
| `:common:compileStbetaDebugJavaWithJavac` | BUILD SUCCESSFUL, exit 0 |
| `:common:lintStbetaRelease :smarttubetv:compileStbetaDebugJavaWithJavac` | BUILD SUCCESSFUL, exit 0（lint 日志仍出现本机 JDK 11 的 `WorkManagerIssueRegistry` class-file 61 提示，属既有环境现象） |
| `:smarttubetv:lintStbetaRelease` | **BUILD SUCCESSFUL, exit 0**（1 m 8 s） |
| GitHub Actions run 35510576265（JDK 11 测试 job） | **success**；下载 `unit-test-reports` 解析 XML：**67 suites / 529 tests / 0 failures / 0 errors / 0 skipped**（与本机整模块结果一致） |
| GitHub Actions run 35510576265（JDK 17 publish job） | **success**；双模块 release lint、`:smarttubetv:assembleStbetaDebug`、`apksigner verify`、prerelease 发布与 artifact 上传全部通过；VirusTotal 按设计跳过 |
| 候选独立复核（本机 SDK build-tools 37.0.0 + JBR 17） | universal APK 45,130,107 B，SHA-256 `a77af5fc941dcc11cd7c0075a8f1d52aa764c0610a569d2f247165d2d6e9fe43` 与 `SHA256SUMS.txt` 一致；`apksigner verify` = `Verifies`（v1/v2），证书 DN `C=US, O=Android, CN=Android Debug` / SHA-256 `9fb71b8a…`（debug 回退）；`aapt` = versionCode 2443 / versionName 32.53-nightly-25 / minSdk 17 / targetSdk 34 / universal 含 arm64-v8a+armeabi-v7a |
| `git diff --check` / `python -B scripts/check-development-docs.py` | 见当天记录最终检查 |

**已执行（远端，同一 SHA `6c70e370`）：** GitHub Actions 必需范围与整模块测试、双模块 release lint（JDK 17）、APK 组装、`apksigner` 校验与 prerelease 发布，见上表。

**仍未执行：** 设备安装与电视验收、真实 Key/付费调用、真实 Android Keystore 与备份导出、API 17–22 设备、项目签名 RC（缺 4 个 Secrets）。远端门禁通过只说明代码门禁，不替代电视验收；本轮候选是 debug 回退签名，**覆盖安装 nightly-24 会被拒绝，需先卸载（清空应用数据与 Key）**。

## 5. 顺序差异与理由

骨架建议顺序是"先修 K1/K2 → 上下文 → 规则断句 → 强制重翻 → 最终交付"。本轮实际顺序为 **修复 → 上下文（第 2 步）→ 强制重翻（第 4 步）**，第 3 步（规则断句）未开始。理由：

- 强制重翻建立在既有身份结构（translation generation、内容事务、缓存/planner/会话上下文）之上，是一个自成闭环、可完整测试的用户可见动作。
- 规则断句（K4/K5）要求同时交付算法、派生时间轴、显示原子替换、边界驱动调度与派生导出；计划明确"算法与显示不能只完成其一"。在剩余预算内无法完整落地时，落地半成品会留下未接线的生产类或不可回退的显示路径，违反"红灯的树比未完成的增量更糟"。

因此本轮选择"少而完整"，把 K4/K5 作为明确的下一步，而不是标记为已完成。

## 6. 下一步（按优先级）

1. **K4/K5**：`SubtitleRuleSegmenter`（保守 native 边界合并、稳定来源映射、局部回退与 LONG_UNSPLIT 计数）、`SubtitleSegmentTimeline`（`segmentId/memberItemIds/startUs/endUs/sourceText/ruleVersion`）、planner 选择 raw/derived、单显示入口的原子替换、边界驱动显示更新、导出增加 `segmented-*.srt`。
2. ~~**GitHub-only 门禁**~~：**已完成**（run 35510576265，SHA `6c70e370`，候选 `stbeta-32.53-nightly-25-25-debug`）。
3. **设备验收（进行中）**：按[计划 §9](../kiss-subtitle-features-implementation-plan.md)与使用说明 §7/§4.5 在电视上验收已实现功能；重翻入口的焦点/回执、通知遮挡与上下文档位的实际观感；真实 Key 的最小付费调用另行授权。**安装前注意 debug 回退签名需先卸载旧包。**
4. **独立缺口**：项目签名 RC（缺 4 个 Secrets）、API 17–22 设备、真实 Keystore/备份导出、导出译文形态。

## 7. 隐私与边界

- 未发送任何用户字幕、设备日志或凭据到第三方；本文件不含真实 Key、URL 签名或字幕正文。
- 本轮的"实现"只等于源码 + 本地自动测试 + 编译/lint；不代表发布、签名、升级或设备可用。
