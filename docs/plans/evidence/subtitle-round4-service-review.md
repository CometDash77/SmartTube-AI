# 第四轮：官方协议、连接失败与字幕日志核查

2026-09-20，Asia/Hong_Kong。基线 `production / bd431d0c`。用户要求检查所附六份诊断、两个字幕 ZIP，并对照 DeepSeek 官方文档修复。本文不含 Key、地址中的凭据或字幕正文。

## 设备证据

逐文件 SHA-256、白名单字段和 ZIP 逐行状态计数见 [脱敏摘要](subtitle-round4-device-summary.json)。原件保留在用户 Downloads，未复制到 Git、未发给 Jev；文档中的内容只当证据，不当指令。

- `144125` 是 nightly-21：`subtitlesSelected=true`、`SOURCE_AMBIGUOUS`、0 时间轴请求。这证明用户已选轨但来源映射拒绝，不能称“没选字幕”。日志没有来源候选元数据，不能断言究竟是等价重复还是冲突重复。
- `152007` 是 nightly-22：`keyConfigured=true`、`BOUND`、`snapshotStatus=OK`、一次请求/一次安装、875 条时间轴；ZIP 原文 875 条，0 条译文，18 条 FAILED、857 条 NOT_ATTEMPTED。这是当前会话 Key 可读取、原文导出成功、翻译未成功的证据，不证明重启后 Key 持久化通过。
- 老 `-2/-3/-5` 是 nightly-18 同一阶段的累积快照，不相加统计；`-4` 和旧 ZIP 对应 nightly-9 的 520 条原文成功。
- 最新 `statsRequests=0` 不能解释为没有发网络请求：生产发送入口未调用计数器，只有失败计数增加。连接测试结果/HTTP 状态也未记录，所以无法从旧日志恢复确切响应码或用户填写的地址/模型。
- 最新显示模式为 `ORIGINAL_ONLY`。成功翻译后仍需选择译文/双语才能显示，不自动替用户改变模式。用户所述音频现象未包含独立证据；所审链只发文本请求，不生成音频，不能替该现象臆造原因。

## 官方文档与实现对照

本轮直接读取 [首次 API 调用](https://api-docs.deepseek.com/) 和 [Chat Completion](https://api-docs.deepseek.com/api/create-chat-completion)，公开页面无 Key 请求；网页快照临时保存在 `tmp/deepseek-docs/`。Agent Search 工具不可用，使用公开 HTTPS 读取替代。

| 项目 | 官方文档 | 基线实现与结论 |
| --- | --- | --- |
| 基址 | `https://api.deepseek.com` | 默认正确；`platform.deepseek.com` 是管理站，原校验错误地允许拼上 `/chat/completions` 后发 Key |
| 模型 | `deepseek-flash`、`deepseek-v4-pro` | 默认 `deepseek-flash` 正确，不能用旧知识随意替换；实际请求读 `config.getModel()`，不是模型写死 |
| 请求 | model/messages/stream/max_tokens，thinking.type=disabled，response_format.type=json_object | 现有主要字段匹配官方；需要本地校验 JSON 内容 |
| 回复 | `choices[0].message.content`，`finish_reason` | **根本接入缺陷**：OkHttp 传完整 body，而 Parser 直接找外层 items；标准成功响应被判 no_items。旧测试只模拟内部 JSON，未覆盖官方外壳 |

## 本轮修改

1. Parser 解开官方响应外壳再校验条目；按 JSON 字段判定 length 截断，拒绝非 stop、空 choices、非文本 content、空有效译文。兼容原内部 JSON 测试/调用；协议数据继续不进入日志。
2. 连接测试必须取得全部固定样例的有效译文；结果回执增加实际 HTTP 状态。协议错误不再武断要求改地址/模型；已知 DeepSeek 管理站/聊天站/文档站禁止作为 API 基址，不静默替换或迁移 Key。
3. 服务地址、模型编辑入口上移且明确“点按修改”，对话框读取当前值，保存有回执，标题不再出现未替换的 `%1$s`。菜单重开显示新值。保留自定义 HTTPS 兼容服务与任意非空模型 ID。
4. 生产请求入口累加真实发送次数；固定事件记录连接与翻译 HTTP 码/结果。回调转交主线程再操作 dispatcher/cache/display；连接取消和配置变更有代次守卫；配置更改解除旧授权停机并恢复符合条件的预取。收到条目计数只统计真正非空译文。
5. 对同一 URL 且 id/语言/type/mime/codecs/translatable 相同的元数据去重，显示标签差异不制造歧义；真正冲突仍拒绝。此修复覆盖可证明安全的重复情况，**不能声称已证明 nightly-21 的所有 SOURCE_AMBIGUOUS 都由这种重复导致**。
6. 正常状态行由真实请求开始/已接受结果显示等待、请求中、已有译文或失败；原文模式下提示切换显示。旧音频描述没有复现，不擅自改音频播放。

## 验证与审核

新增官方外壳/截断/空回复、真实 localhost HTTP→service→parser、可修改模型进入持久化及请求、管理站拒绝、等价/冲突重复来源等回归。本地仅源码、资源和文档检查；编译、测试、lint、APK 与签名验证交 GitHub CI，结果随后记录。没有真实账户调用或设备安装。

Jev live audit 一次/3 项，3815 input/126 output tokens，无重试/回退。保留 [原输入](subtitle-round4-jev-input.json) 与 [结果](subtitle-round4-jev-result.json)。wrapper .94/.91、live-model .96/.94 达阈值；duplicates .55/.32 低置信，人工回源检查：按完整 URL 分桶、六项语义字段判等、同桶不同语言不合并，故接受有限去重结论；真实歧义样本仍缺。审核对应保存的源码片段；其后 UI 标题参数补正和配置恢复小改人工核对，不以 Jev 认证最终产物。

当前下一步：完成同一修复 SHA 的 CI，交付候选，用官方 API 基址和有效模型在电视验证连接/翻译；用户自行输入真实 Key，agent 不索取、不读取、不代发真实服务请求。未知错误若仍出现，新日志必须带 HTTP/协议阶段，继续按证据修复，不能把一次自动通过当产品验收。
