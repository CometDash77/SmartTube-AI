# 字幕功能当前状态

更新：2026-09-20（Asia/Hong_Kong）。本页为当前状态入口，旧文件保留历史证据。

- **产品验收未通过，当前故障已转到翻译服务链**。用户提供 nightly-22 日志：keyConfigured=true，ASR 来源 BOUND、时间轴 OK、875 条原文导出成功，但译文 0；ZIP 中 18 条 FAILED、857 条 NOT_ATTEMPTED。Key 当前会话可用，不等于重启持久化已验收。
- nightly-21 的另一份日志显示已选轨但 SOURCE_AMBIGUOUS，不能再归因用户未选字幕。等价重复与真实冲突的具体元数据尚缺，修复只安全合并前者。
- 官方文档确认 API 基址 https://api.deepseek.com、deepseek-flash 有效。模型不是写死；实际缺陷是生产请求只发字幕 payload、缺 model/messages，且标准响应 choices[0].message.content 未解包，被误报检查地址/模型，旧请求计数也未接生产入口。
- 第四轮修复 `4dca086a71741c78fc9da2dc0d70c5cea3625bd1` 已推送，run `35497368918` 失败，新增断言揭示 request body 缺 model。已补齐完整请求接线，待修正后 CI，尚无本轮新 APK。范围与证据见[第四轮核查报告](evidence/subtitle-round4-service-review.md)。没有调用用户账户或上传原始字幕/日志。
- 上一候选 nightly-22 / 4a83f633 已通过 447 tests 和 CI，但仍含本次协议缺陷；不能继续作为服务修复候选。所有 debug 候选仍非稳定升级身份，不擅自卸载设备或承诺无损覆盖。
- 唯一下一步：完成第四轮同 SHA 的 CI 并交付新候选，电视按官方 API 地址/模型验证连接及双语显示；整体 W0–W5 重改继续保留，不能因本次定点修复而声称全项完成。

历史依据：[当天记录](../development/2026-09-20.md)、[重改计划](subtitle-production-rework-plan.md)、[第三轮计划](subtitle-round3-implementation-plan.md)。
