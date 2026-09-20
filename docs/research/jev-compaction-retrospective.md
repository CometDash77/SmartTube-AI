# Jev 辅助工具与 Codex semantic compaction：证据复盘

日期：2026-09-20（Asia/Hong_Kong）。检查基线：`production / 18e8aeea`。本次只做复盘，没有修改运行代码、hook、AGENTS 或 Skill，没有实现 compaction。

后续材料提示：[当天日志](../development/2026-09-20.md) 的 17:44 条目另记参考实现与当前 Codex 接入研究。这是另一阶段记录，本报告保留原复盘证据边界；该条目的测试未在本次提交整理时重跑，其当前能力判断也不能倒推历史版本。后续实现仍需单独确定范围，本次仅提交文档。

## 结论与范围

**本仓库可证明的是 Jev rank/audit 辅助工具已实现、测试并被调用；不能证明曾实现过失败的 Codex 自动 semantic compaction。** 用户本轮澄清“并没有，这是新要求”，因此不再假设另有失败分支。新目标与旧工具必须区分，不能为新要求编造历史失败链。

检索范围：本地设计/计划、根 AGENTS、项目 Skills、两日开发记录、所有本地 Git refs 的相关提交与文本变更、当前跟踪/项目说明文件。未检查其它项目、全局会话私有记录或不可达 Git 对象。`git log --all` 不等于穷尽所有曾存在但已删除的记录。下面的“未发现”均限定于此范围。

证据优先级：固定 commit 源码/diff → 保存的测试/API 结果 → 当时说明与复盘记录 → 本轮推断。旧复盘中的 transcript 摘录是已保存的二级证据，本轮未取得原始会话独立重放。当前 Codex 提供自动上下文压缩这一事实，也不能倒推出当时版本的扩展 API。

## 历史重建

| 阶段 | 持久化证据 | 能确定的事实 |
| --- | --- | --- |
| 初始工作区 | `0dd1755e` | 项目 build Skill 与工程说明；尚未加入 Jev triage |
| Jev 工具落地 | `715324da73952d3bb88a9b46571cd0afbb07e521` | 新增 `smarttube-jev-triage`、Python CLI、15 项策略测试、评估结果与 DSH 薄适配；AGENTS 要求适当时主动调用 |
| 初始设计选择 | 同提交 `references/evaluation.md:40–50` | 阅读 `fast-jev-compaction@e3f262a7` 后只采用原文保留/证据保护，明确拒绝自动 transcript rewrite/hook 安装 |
| 强化使用指引 | `dbad8e0a` 的 AGENTS diff | 从适用时使用升级为重大审查主动尝试；仍是 agent 行为约定，没有 runtime 注册代码 |
| 使用中的执行复盘 | `33e0fd2f`，`docs/plans/evidence/subtitle-retro-tools-jev-input.json` | 保存了宽泛搜索、重复拉取、错误查询、写入与校验并发、无命中替换误报、摘要自证等问题；不是 compaction 故障日志 |
| 修正证据流程 | `e8720428` 的 Skill diff | 新增原始源码证据、结果立即保存、避免不变输入重复调用等要求；CLI 无功能变化 |
| 本轮核验 | `715324da..18e8aeea` 路径历史 | `scripts/jev.py` 只在初始提交加入；未发现后续 Codex 历史改写实现。15 项现有测试本轮全部通过 |

当时真正想实现的是：减少大批模糊代码候选的阅读负担，辅助判断“证据是否支持某项结论”。不是压缩全部对话，更不是让 Jev 生成接替 Codex 的摘要。初始设计没有量化要求自动替换会话历史。

## 真实运行路径与实现程度

`agent 检索 → 保存 task + candidates/checks JSON → 显式执行 jev.py rank/audit --live → native typed Decisions 请求 → Python 校验概率/阈值 → stdout 报告 → agent 消费/人工裁决`

对应 `715324da` 的 `scripts/jev.py:44–70`（输入）、`:172–211`（网络/失败处理）、`:213–241`（报告）、`:244–261`（CLI）。`rank.context` 是返回 JSON 中的候选子集，不是 Codex context 对象；`source` 是定位字符串，不是 session/history 句柄。脚本不读取或写入 Codex 会话、不注册 hook，也没有 message role、tool_call_id 或会话版本字段。

因此，“代码看起来实现但未进真正运行路径”应分两种判断：

- **Jev 调用本身进入了实际人工调用路径。** `evaluation-results.json` 保存四次历史 live 调用，后续审查也有结果；不能说它是从未执行的死代码。
- **Codex 自动 compaction 路径未建立。** Skill 被发现、CLI 成功、报告字段叫 context，都不能证明触发压缩或替换下一次模型请求。对旧设计这是明确的非目标；对新要求则是尚未实现的核心接入缺口。

## 正确设计与错误判断

值得保留：有限选择的语义判断与精确代码分工；批处理独立问题；保留来源与原文；pin/不确定候选不删；概率与 confidence 分开；服务失败返回全部候选；凭据不跨 provider；拒绝把 Jev 结论当授权/任务完成证明。历史源码、测试和本轮复跑支持这些局部性质。

不能从现有成果推导的假设：

1. **检索片段更少 = Codex 会话更小。** 历史测量为片段字符和报告字符，不是实际下一次模型请求 token。`evaluation.md:58–96` 主动说明没有 Astra A/B、独立生产 holdout 或端到端收益证明；本轮没有发现它正式宣称自动压缩成功。
2. **Skill 可发现 = 自动接入。** `evaluation.md:113–126` 明确只是人工触发路由评估。AGENTS 是指引，不是执行拦截器。
3. **Jev 支持摘要 = 验证了实现。** `33e0fd2f` 的既有复盘 `jev-recursion` 项记录把状态表/测试名当证据、问下一步价值，随后发现摘要过期和 lint 证据不全。这是有记录的执行错误；不能归咎于 Jev 没有读取未提供的代码。
4. **相关性可替代历史完整性。** 现有 rank 没有 turn、call/result、恢复点语义。将它直接用于删除会话历史会超出已验证能力；这是新设计风险，不是已经发生过配对破坏的证据。

## 故障层逐项判断

| 层 | 证据结论 |
| --- | --- |
| 架构 | advisory triage 的目标与实现一致；用它直接宣称自动 compaction 才是层级错配，未找到这种已部署改造 |
| Codex 接入点 | 没有注册或注入路径；不能把缺实现说成已证实 Codex 拒绝接入 |
| context/history 生命周期 | 未实现/未测试；没有自动压缩、恢复、fork、进行中 turn 的失效轨迹 |
| tool call/result 配对 | 输入 schema 不表达这些对象；没有配对保护，也没有历史破坏实例 |
| Jev 调用 | 有历史 live 成功证据，本轮 live 也成功；不是已证实的阻塞根因 |
| fallback | 对合法输入及已捕获 provider 错误保留候选；非法输入由 CLI 返回错误并要求调用方使用原输入，不能称一切异常都自动恢复 |
| 测试验证 | 策略测试通过；对 Codex 自动运行、生命周期及完整 token 成本没有验证 |
| Hermes / Claude 移植 | 只找到社区 compaction 方案被参考并明确拒绝 hook 的记录；未找到 Hermes 实现或 Claude hook 移植，不能定罪 |
| Codex 原生能力 | 旧资料没有固定 Codex 版本的原生压缩/API 能力对照。不能断言当时忽略了某个已可用 hook；新设计必须先核对，不能以今日能力倒推历史 |

## 三类问题

### 1. 实现错误

本次未证实旧 triage 代码导致 compaction 失败。已证实的**新需求实现缺口**是：没有宿主接入、结构化消息与配对模型、会话恢复/事务写入路径、原生压缩协调。它们不能伪装成已实现功能，也不应误列为旧有限工具的回归 bug。

### 2. 执行错误

既有复盘证实或记录：过宽搜索产生巨大输出；同一 README 重复拉取；不正确搜索语法后过早解释空结果；写入与校验并发；替换零命中却宣称已改；以摘要喂给 Jev 再当独立验证。它们增加上下文负担、削弱证据质量，无法靠压缩算法补救。具体依据见 `33e0fd2f` 的 `subtitle-retro-tools-jev-input.json`，修正规则见 `e8720428` 与 `docs/development/review-handoff.md`。

本轮也出现了检索输出截断与两次 PowerShell 路径/语法错误；已改用窄路径查询。它们是本轮执行质量问题，不作为历史 compaction 失败原因。

### 3. 环境/能力限制

当时记录项目没有 agent runtime/MCP 路由系统（`evaluation.md:9–15`）；没有固定版本的 Codex hook 能力证据。因而可以判定**宿主能力尚未论证**，不能判定“Codex API 当时绝对不支持”。当前工具也未暴露任意历史替换接口；这仅约束当前会话，不是跨版本结论。DSH PTC 的 SDK/执行规则明确属于 DSH，不构成 Codex 接口契约。

## 重新实现前必须完成的事项

1. 定义目标：检索预筛、工具输出裁剪、还是替换会话历史；分别规定触发点、状态所有者与衡量指标。不要共用 compaction 名称掩盖差异。
2. 固定 Codex 版本及运行方式，先阅读该版本文档/源码，列出原生自动压缩、手动压缩与可扩展接口。用最小无敏感数据探针证明“触发 → 修改 → 下一次模型确实使用”的完整路径；没有受支持接口就只保留显式工具预筛，不操作内部历史文件冒充接入。
3. 在真实宿主路径上验证消息依赖：并行/多轮工具调用与结果、未结束调用、取消、中断、失败、重试、恢复/fork、多模态、用户更正与系统约束。未配对结果不能被留下，所需调用也不能被单独删除。
4. fallback 必须覆盖原会话无损保留、旧代次结果拒绝、超时/部分响应/非法输入、写入中断和可恢复性；不能仅证明 CLI 返回了全部 candidates。
5. 冻结代表性任务，比较原生机制与新增方案：实际请求 token、总成本（含 Jev/Skill/报告）、延迟、任务正确率、遗漏证据恢复率。先读全部内容再筛选无法节省已经消耗的上下文。
6. 保留修改前失败、修改后通过的宿主级验证；日志记录固定原因与匿名标识，不收集凭据或原始私有对话。

以上是下次设计的前置事项，未实施，也不要求本次新增测试或重构。

## 哪些经验应写回指引

| 位置 | 建议 | 本轮处理 |
| --- | --- | --- |
| AGENTS.md | 只有宿主实际调用路径有证据才称集成；区分能力未论证、明确不支持和未实现 | 保留为建议，不扩充未经验证的运行规则 |
| Jev Skill | 明确 rank 是检索预筛；context 字段不表示 Codex history；收益计入报告/Skill/Jev 成本 | 大部分边界已有，建议只补语义区别，避免重复规章 |
| evaluation/design | 新 compaction 必须单独 capability matrix、消息生命周期及真实请求验收，不把旧 15 项策略测试改称集成测试 | 由本复盘提供下一次设计输入 |
| review-handoff | 保留已落实的原始证据、串行写入验证、结果即刻落盘等规则 | 无需再复制一套 |

## 本轮 Jev 与验证

一次 live audit、5 项原子判断，模型 `typesafe/jev-1.13-20260917`，6816 input / 214 output tokens，1133.28 ms，API 报价 USD 0.000286272；无重试/回退。只发送固定提交的工具源码/设计/历史复盘摘录，没有设备日志、字幕或密钥。

| 项 | 判断 / 概率 / confidence | 人工裁决 |
| --- | --- | --- |
| scope | supports / .91 / .87 | 接受：原始设计拒绝自动 hook，CLI 只输出报告 |
| lifecycle | insufficient / .88 / .81 | 接受证据缺口：schema 与输出不能证明 call/result 和恢复正确 |
| fallback | supports / .89 / .84 | 接受限定范围：合法 rank 输入、被捕获的 provider 故障 |
| measurement | contradicts / .98 / .97 | 接受：历史材料明确没有整段 Codex 的 token/收益证明 |
| prior-audit | contradicts / .94 / .91 | 接受历史复盘所记录的摘要自证问题；不冒充重新取得原 transcript |

原始[输入](evidence/jev-compaction-retrospective-input.json)与[结果](evidence/jev-compaction-retrospective-result.json)保留。Jev 只检查所给证据关系；源码完整性、能力边界与最终结论由人工复核。

本轮运行 `python -B -m unittest discover -s .agents/skills/smarttube-jev-triage/scripts -p test_jev.py -v`：15/15 通过。这是当前策略复跑，不是历史 CI 重跑或 Codex 自动 compaction 验收。没有修改应用、AGENTS、Skill，也没有重新构建 APK。
