# Kiss agent 工作复核与继续实施骨架

日期：2026-09-20，Asia/Hong_Kong。审查基线：`production` / `91982d5e` 加当前未提交 K0–K2 工作树。用户要求检查 agent 是否合理，不合理则写继续完成的骨架；本轮不修改应用、不替用户通知或启动另一 agent。

## 结论

总体方向合理，保留已有增量实现和测试；**不能按“K0–K2 已完成，直接进入 K3”继续**。先修复下列生产接线缺口，再实现余下功能。不需要新架构、重新研究 Kiss、全库重构或增加审批轮次。

合理之处：以已验收 nightly-24 为基线；默认基础上下文、关闭断句；复用现有设置、缓存、串行 dispatcher 和非模态提示；未引入第二网络栈作为通用框架。上下文、断句的设置先落地是可以接受的中间态，但行为尚未实现，不能发布为完整功能。

## 阻断继续扩功能的发现

源码路径以下相对 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/`，行号为本轮审查工作树。

1. **P1：生产配置变更绕过取消入口，旧译文可污染新配置缓存。** `app/presenters/PlaybackPresenter.java:418` 仍调用 `getController().onConfigurationChanged()`，没有调用新加的 `AiSubtitleSessionBinder.onConfigurationChanged()`（`exoplayer/other/AiSubtitleSessionBinder.java:100`）。前者虽使 token 失效，但 dispatcher 未注册到 controller 的取消集合；生产相关路径搜索未找到 `.register(...)`。随后 presenter 清 cache、重装同一 timeline，仅重置 planner，不取消在途调用。旧成功仍可通过 dispatcher 的 batch identity 检查并在 `SubtitleTranslationDispatcher.java:188` 写 cache；显示又使用当前 token，因此不能靠显示 token 补救。切换目标语言/上下文时尤其可见。修复必须走统一内容配置事务，并在写 cache/history/status 前挡住旧回调。
2. **P1：连接测试的互斥只有一个方向。** `PlaybackPresenter.java:727` 仅在测试启动前检查 dispatcher busy，`:765` 另起连接请求，不预留 dispatcher 槽位、不暂停或阻止下一次预取；`SubtitleTranslationDispatcher.tick()` 不读取连接测试状态。复现时序：翻译间隙启动慢连接测试 → 下一 tick 启动翻译 → 两个 AI 请求同时在途。须覆盖两种启动顺序、测试取消迟回与恢复；仅新增 BUSY 枚举不构成共享槽位。
3. **P2：通知实际交付未使用最新状态和完整身份。** `PlaybackPresenter.java:684` 前后在 post 之前捕获 show/aiEnabled，runnable 只检查 sourceKey，未校验 translation generation。通知入队后关通知、关 AI、同来源改配置，仍可能弹旧通知。policy.reset() 也不会撤销已排队 runnable；UI 只 showMessage，关闭开关没有取消当前提示。应在交付时检查最新设置及捕获的 session/generation，并在关闭/release 时清待显示任务和现有提示。
4. **P2：预取的未来译文真正显示时没有通知入口。** `PlaybackPresenter.java:489` 只在网络成功监听中调用 onTranslationShown；`SubtitlePrefetchLoop.java:49` 的缓存帧显示路径没有此事件。若最后一批只含未来条目，稍后进入该帧且没有后续请求完成，就永远不发首次可见译文通知。把事件接到实际接受并显示非空译文的公共入口，并保留去重；仅计数 getter 不够。

这些是静态调用链和明确时序反例，不冒充本轮动态复现。前两项与原计划 K1 契约直接冲突，后两项与 K2 契约冲突。

## Ponytail 简化建议

- `exoplayer/other/SubtitleLoadNotificationPolicy.java:L76: shrink:` 删除只转发赋值的私有 `mGenerationGuard`，在 setter 直接赋值。确定可净减 4 行；小修随该文件必要修改一起做。
- 不为摘要、重翻和连接测试分别造三套队列/取消管理器；在现有 dispatcher 附近增加最小的共享占用状态或入口即可，具体设计以终止回调和现有调用者为准。
- 不按“一字段/一 getter/一轮全套测试”碎片化推进。每个用户可见行为完成生产接线及相关验证再记录里程碑。

Ponytail 可确定的小清理净值：`net: -4 lines possible.` 这不是重构收益承诺，也不是本次主要问题。

## 可直接交给实施 agent 的骨架

沿用[原计划](kiss-subtitle-features-implementation-plan.md)的预算、兼容性、原始时间轴不变和单一显示入口契约；以下收紧执行顺序，不删减用户要求的四项功能。

1. **修复 K1/K2，再报告基础完成。** 内容配置走真实 binder 事务；AI 操作共享占用与最小间隔；通知在实际帧显示入口产生，延迟交付核对完整身份/最新开关，可撤销。补四个发现的生产装配回归：旧请求迟回不得污染缓存/显示/状态；测试和翻译两种顺序均无并发；通知排队后关开关/换代/release 无旧提示；未来缓存帧进入画面只提示一次。测试必须经过生产调用接缝，不能只测孤立新方法。
2. **完成上下文，先连贯再视频增强。** 修正全时间轴邻文；仅保存已验证且有界的译例；将所选档位实际接入生产请求 body。再复用 transport 做一次性摘要，专属 schema/parser、次数与超时上限、失败回退、冻结上下文。沿用原预算；不新增供应商层或跨视频持久缓存。模拟 HTTP 检查输入、次数、失败后恢复及身份隔离，不承诺精度提升。
3. **完成规则断句的整条链路。** 保守 native 边界合并、稳定来源映射、局部回退；接入翻译、原子显示与一致快照导出。算法与显示不能只完成其一。原始 SRT 不变；派生文件明确命名。自动验证空白区间、重叠槽位、seek、关闭规则即时回原文；设备时差独立待验。
4. **完成强制重翻。** 在最终身份结构上做一个事务：失效 → cancel/等待终态 → 清本来源译文/历史/失败预算/planner → 保留原始时间轴及允许保留的摘要 → 当前窗口重排。连续点击合并，保留 429 等待；旧成功、旧失败、旧通知不能跨代生效。明确只完成当前窗口，不声称全片完成。
5. **最终交付。** 更新设置说明、诊断、计划状态和当天进度；按现有发布授权与 GitHub-only 工作流，用同一最终 SHA 获取必需测试、lint、组装与候选证据。提交/推送/发布若不在已获授权内，就交付可审阅代码和待执行步骤，不把本轮审查当作新增发布授权。真实 API、设备焦点/时差/导出验收分别报告；没有设备不阻塞前四步。

## 验证与 Jev 裁决

- 470 tests / 0 failures 是前 agent 在 19:25 日志记录的本地结果，本轮没有重跑，也没有将其当作 CI 或上述时序回归已通过。原计划 K7 明确“不在本地跑 Gradle”，此前执行方式偏离该约束；后续遵循 GitHub-only，无需为了审查再跑本地构建。
- 一次 live Jev audit，3 个独立主张，4,933 输入 / 131 输出 tokens；无服务失败或重试。精确[输入](evidence/kiss-agent-review-2026-09-20-jev-input.json)和[结果](evidence/kiss-agent-review-2026-09-20-jev-result.json)已保存。
- configuration-cancel：Jev supports 0.84/confidence 0.75，未达阈值；人工进一步查看 controller 取消集合、生产注册调用及 setTimeline 路径，裁决 contradicts。新增 binder 方法存在不代表生产 caller 使用它。
- test-slot：Jev insufficient 0.66/confidence 0.49；人工连读测试启动、dispatcher tick 和 prefetch loop，裁决 contradicts，反例如上。
- queued-notice：Jev supports 0.80/confidence 0.69，未达阈值；人工核对变量捕获位置，裁决 contradicts。捕获旧开关不是在交付时读取当前开关。
- 本轮只写复核、骨架及证据；不修应用、不启动另一 agent、不触发发布。文档检查结果记于当天进度。

当前下一步：实施 agent 从第 1 步修复 K1/K2 接线开始，然后顺序完成其余步骤；不要直接进入旧日志指定的 K3。

## 实施结果（2026-09-20，后续实施 agent）

- 第 1 步**已完成**：四项发现全部修复并各带生产接缝回归（旧请求迟回不污染缓存/显示；连接测试与预取双向互斥；通知交付时重读开关并核对来源与代次；未来缓存帧真正显示时只提示一次），Ponytail 的 4 行清理一并完成。
- 第 2 步**已完成**：全时间轴邻文（正序、稳定 ID 去重、排除本批条目）、仅收已验证且有界的译例、档位真正进入生产请求 body、一次性视频摘要（固定 schema/parser、5s 上限、单次尝试、失败回退、冻结上下文）。
- 第 3 步（K4/K5 规则断句整条链路）**未实施**；第 4 步 K6 强制重翻**已完成**（事务、旧代次丢弃、可操作回执）。顺序差异与理由见[实施状态](evidence/kiss-implementation-status-2026-09-20.md) §5。
- 第 5 步只完成文档/状态部分：本地 `:common:testStbetaDebugUnitTest`（`exoplayer.other` 范围）66 suites / 519 tests / 0 failures、`:common:compileStbetaDebugJavaWithJavac`、`:common:lintStbetaRelease`、`:smarttubetv:lintStbetaRelease`、`:smarttubetv:compileStbetaDebugJavaWithJavac` 全部 BUILD SUCCESSFUL；**未 commit/push、未触发 GitHub Actions、未组装或签名 APK、未设备验收、未真实付费调用**。
- 逐项证据、命令与仍缺项见 [Kiss 实施状态](evidence/kiss-implementation-status-2026-09-20.md)；当天记录见 [2026-09-20](../development/2026-09-20.md)。
