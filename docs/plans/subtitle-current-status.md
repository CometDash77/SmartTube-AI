# 字幕功能当前状态

更新：2026-09-20（Asia/Hong_Kong）。本页为当前状态入口，旧文件保留历史证据。

- **产品验收未通过**：用户在 nightly-21 候选说明后再次报告“失败”，要求重改。用户已澄清：点击保存 API Key，无论输入什么都提示保存失败。这是本地保存链故障，不是服务鉴权或字幕获取失败。
- 当前修复候选：[stbeta-32.53-nightly-22-22-debug](https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-22-22-debug)，SHA `4a83f633f6d563344fd2ae348cae1838a4719195`。run `35495588228` success；59 suites/447 tests/0 failures/errors/skipped，lint、打包、签名检查与 prerelease 发布均通过。旧 nightly-21 仍含保存缺陷。CI 不等于设备验收。
- 核查基线 `1c2eb06d` 相对旧候选仅文档差异。后续 Key 修复提交 `4a83f633` 已推送，run `35495588228` 的 447 项测试与 lint/build/publish 均通过。R1–R4 没有重写字幕来源获取，不能写成整条生产链已修复。
- 本轮明确发现：AI 开启后的“翻译中”仍由配置和绑定状态推导；原生显示与额外时间轴获取是不同链。另已定位 Key 加密传入外部 IV 与 AndroidKeyStore 默认随机加密策略冲突，且创建存储未检查实际可用性。Key 修复的完整 CI 已通过，尚待设备验证。
- 历史签名证据为 debug 回退候选；不是稳定升级身份，不能将该 APK 原样提升正式版。没有本轮重新检查 Secrets，也没有安装/卸载设备。
- 当前方案：[字幕与 AI 翻译生产链重改计划](subtitle-production-rework-plan.md)。**整体重改未实施；当前先修复用户明确指出的 Key 保存缺陷**。
- 唯一下一步：用 nightly-22 验证占位值保存、取消、清除和重启读取（AI 关闭，不测试连接；仅会话存储预期重启丢失）；整体字幕重改仍保留 W0–W5，不把这次保存失败误记为字幕来源故障。

历史依据：[当天记录](../development/2026-09-20.md)、[第三轮计划与 CI 摘要](subtitle-round3-implementation-plan.md)、[第二轮独立复核](evidence/subtitle-round2-independent-review-2026-09-20.md)。
