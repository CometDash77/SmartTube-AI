# 字幕功能当前状态

更新：2026-09-20（Asia/Hong_Kong）。本页为当前状态入口，旧文件保留历史证据。

- **nightly-24 已获用户确认：APK 可用，首次打通所需工作流。下一阶段改善精度。** 此结论来自用户实际使用反馈；新版日志由用户交给后续 agent，不代表全部设备、长期运行、重启持久化或稳定签名均已专项验收。
- 历史故障：nightly-22 日志为 keyConfigured=true、ASR 来源 BOUND、时间轴 OK、875 条原文导出成功，但译文 0；ZIP 中 18 条 FAILED、857 条 NOT_ATTEMPTED。该结果不再代表 nightly-24 当前状态。
- nightly-21 的另一份日志显示已选轨但 SOURCE_AMBIGUOUS，不能再归因用户未选字幕。等价重复与真实冲突的具体元数据尚缺，修复只安全合并前者。
- 官方文档确认 API 基址 https://api.deepseek.com、deepseek-flash 有效。模型不是写死；实际缺陷是生产请求只发字幕 payload、缺 model/messages，且标准响应 choices[0].message.content 未解包，被误报检查地址/模型，旧请求计数也未接生产入口。
- **当前验收候选：[stbeta-32.53-nightly-25-25-debug](https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-25-25-debug)**，SHA `6c70e370`，run `35510576265` success。67 suites / 529 tests / 0 failures/errors/skipped；双模块 release lint、组装、`apksigner` 校验与 prerelease 发布通过；独立复核证书 `CN=Android Debug` / `9fb71b8a…`，**与 nightly-24 证书不同，覆盖安装会被拒绝，必须卸载旧包（清空应用数据与 Key）**。验收范围见[计划 §9](kiss-subtitle-features-implementation-plan.md)。
- 上一候选：[stbeta-32.53-nightly-24-24-debug](https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-24-24-debug)，修复 SHA `164508024b6611327f6349295a967b5385ebb4c0`，run `35497616104` success。59 suites/456 tests/0 failures/errors/skipped；两模块 lint、组装、签名检查、prerelease 发布均通过。首轮 run `35497368918` 因真实缺 model 缺陷失败且未发布，修正后才完成门禁。范围与证据见[第四轮核查报告](evidence/subtitle-round4-service-review.md)。没有调用用户账户或上传原始字幕/日志。
- 上一候选 nightly-22 / 4a83f633 已通过 447 tests 和 CI，但仍含本次协议缺陷；不能继续作为服务修复候选。所有 debug 候选仍非稳定升级身份，不擅自卸载设备或承诺无损覆盖。
- **Kiss 四项功能实施进行中（2026-09-20）**：审查发现的 K1/K2 四项生产接线缺口已修复并有生产接缝回归；智能上下文（基础/连贯/视频增强 + 一次性摘要）与强制重翻已实现并接线；字幕加载通知已按交付时身份校验重做。**规则断句（K4/K5）尚未实施**（设置项存在但无行为）。逐项状态、证据与缺口见 [Kiss 实施状态](evidence/kiss-implementation-status-2026-09-20.md)，需求见[研究](../research/kiss-subtitle-features-2026-09-20.md)与[增量计划](kiss-subtitle-features-implementation-plan.md) §8。以 nightly-24 为基线；本地 66 suites / 519 tests / 0 failures，双模块 release lint 通过，但**未 push、未触发 GitHub Actions、未组装/签名 APK、未做设备验收或真实付费调用**。W0–W5 保留为历史重改方案与未验事项参考，不自动成为执行队列。
- Jev / semantic compaction 另见[证据复盘](../research/jev-compaction-retrospective.md)：已有 rank/audit 辅助工具，未部署 Codex 自动 compaction。本轮仅分析与文档交付，未重实现功能。

历史依据：[当天记录](../development/2026-09-20.md)、[重改计划](subtitle-production-rework-plan.md)、[第三轮计划](subtitle-round3-implementation-plan.md)。
