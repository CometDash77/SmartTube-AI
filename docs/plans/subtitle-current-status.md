# 字幕功能当前状态

更新：2026-09-20（Asia/Hong_Kong）。本页为当前状态入口，旧文件保留历史证据。

- **nightly-24 已获用户确认：APK 可用，首次打通所需工作流。下一阶段改善精度。** 此结论来自用户实际使用反馈；新版日志由用户交给后续 agent，不代表全部设备、长期运行、重启持久化或稳定签名均已专项验收。
- 历史故障：nightly-22 日志为 keyConfigured=true、ASR 来源 BOUND、时间轴 OK、875 条原文导出成功，但译文 0；ZIP 中 18 条 FAILED、857 条 NOT_ATTEMPTED。该结果不再代表 nightly-24 当前状态。
- nightly-21 的另一份日志显示已选轨但 SOURCE_AMBIGUOUS，不能再归因用户未选字幕。等价重复与真实冲突的具体元数据尚缺，修复只安全合并前者。
- 官方文档确认 API 基址 https://api.deepseek.com、deepseek-flash 有效。模型不是写死；实际缺陷是生产请求只发字幕 payload、缺 model/messages，且标准响应 choices[0].message.content 未解包，被误报检查地址/模型，旧请求计数也未接生产入口。
- 当前候选：[stbeta-32.53-nightly-24-24-debug](https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-24-24-debug)，修复 SHA `164508024b6611327f6349295a967b5385ebb4c0`，run `35497616104` success。59 suites/456 tests/0 failures/errors/skipped；两模块 lint、组装、签名检查、prerelease 发布均通过。首轮 run `35497368918` 因真实缺 model 缺陷失败且未发布，修正后才完成门禁。范围与证据见[第四轮核查报告](evidence/subtitle-round4-service-review.md)。没有调用用户账户或上传原始字幕/日志。
- 上一候选 nightly-22 / 4a83f633 已通过 447 tests 和 CI，但仍含本次协议缺陷；不能继续作为服务修复候选。所有 debug 候选仍非稳定升级身份，不擅自卸载设备或承诺无损覆盖。
- 下一步：以后续提供的 nightly-24 日志与具体字幕样例评估精度，先区分原文识别、时间对齐、译文质量和显示问题，再决定最小修改；不再默认重做已打通链路。W0–W5 保留为历史重改方案与未验事项参考，不自动成为执行队列。
- Jev / semantic compaction 另见[证据复盘](../research/jev-compaction-retrospective.md)：已有 rank/audit 辅助工具，未部署 Codex 自动 compaction。本轮仅分析与文档交付，未重实现功能。

历史依据：[当天记录](../development/2026-09-20.md)、[重改计划](subtitle-production-rework-plan.md)、[第三轮计划](subtitle-round3-implementation-plan.md)。
