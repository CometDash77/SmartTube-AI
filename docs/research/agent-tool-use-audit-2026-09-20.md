# 本次 Harness 会话的 tool use 审计（自查报告）

日期：2026-09-20（Asia/Hong_Kong）。审计对象：本会话（Kiss 字幕 K1/K2 接线修复 → K3 智能上下文 → K6 强制重翻 → K4/K5 规则断句 → 自查修复 → 两轮候选与验收）里**我自己的 tool use**。
方法：brainstorming 式自问（先列原子断言，再逐条找证据或反例）+ smarttube-jev-triage 的一次 live `audit` 批量语义裁决。
结论先行：**没有发现"必须重做"的工具使用错误，但发现 6 类可避免的浪费与 5 类明确错误**；其中最大成本项是对超大文件的整文件 `read`（占被截断输出的 76%）。

## 0. 结论摘要

| 级别 | 发现 | 证据 | 代价 |
| --- | --- | --- | --- |
| 高 | 13 个文件整文件 `read` 造成 22 次输出截断、1.18MB 模型可见上下文 | spill 清单；Jev `read-full-dailyrecord` 判 contradicts 0.96 | 上下文与注意力 |
| 高 | 在 CRLF/mixed 存储文件上直接用 `edit` 插入 CRLF 行，制造 89 行"假改动" | `git diff --check` 报 27 行 trailing whitespace；`git diff --numstat` 由 +95/−1 变 +184/−90 | 返工 + 一次 `edit` 失败 |
| 高 | Gradle 输出只用 `BUILD SUCCESSFUL|FAILED` 过滤，隐藏 javac 诊断 | 第二次运行才看到 `SubtitleConnectionTest.java:66 引用不明确` | 多一次 Gradle（约 45s） |
| 中 | 归一化脚本对 **LF-only** 文件空跑 5 次 | 脚本自报 `blob is LF-only; nothing to normalize`；Jev contradicts 0.98 | 5 次调用 |
| 中 | 同一类字符串转义错误犯 3 次，每次重发整段程序 | 3 条 `Expected ','` / `<lexing error>` | 3 次重发 + 2 次补读 |
| 中 | 归一化重写文件后未重读就 `edit` | `file changed since it was read`；Jev supports 0.93 | 1 次失败调用 |
| 中 | 小步快跑：每 2–3 处编辑跑一次编译，编译失败 5 次 | 5 次编译失败各自的补丁 | 约 5 次额外 Gradle |
| 低 | `present` 传 10 个文件（上限 8） | 工具错误原文 | 1 次调用 |
| 低 | 写已存在的 `tmp/commit-msg.txt` 被拒 | `file has not been read` | 1 次调用 |
| 低 | 两次 `old_string` 未命中（中文引号形态、缩进） | 两次 edit 失败 | 2 次调用 + 补读 |
| 低 | `job_output(wait)` 超过 run_code 120s 默认上限 | `execution deadline reached` | 1 次超时 |

## 1. 证据基础与边界

**可核验的磁盘证据**
- 被截断的工具输出落盘：`%TEMP%\dsh-spill-l7VoNc\session-70e2499d6140\` —— **29 个文件 / 1,555KB**，其中 `read` 22 个 / 1,177KB（**76%**）、`run_code` 5 个 / 256KB（17%）、`pwsh` 1 个 / 56KB（3%）、`job_output` 1 个 / 66KB（3%）。
- 本会话提交：`91982d5e..HEAD` 共 **9 个提交 / 69 files / +6,391 −171**（其中实施提交 `6c70e370` 59 files、`4b002bcc` 17 files、`3eb560b0` 6 files，其余为文档）。
- 工作区变更快照：`%TEMP%\dsh-workspace-changes-ET24tP`（138 文件）。
- 验证产物：`tmp/*.log`（compile/test/verify/lint，本会话生成约 0.8MB 草稿日志，均已 gitignore）。
- 记录：`docs/development/2026-09-20.md` 的 7 条本轮条目、计划书 6 次编辑、`docs/plans/evidence/` 下的候选与 Jev 证据。

**缺失证据（必须声明）**
- **本会话的 harness transcript 未落盘**：`~/.dsh/sessions/--D-VibeCoding-SmartTube-AI--/` 下最近文件为前一日的会话；今天 22:32 前 60 分钟内 `~/.dsh` 无写入。因此**"每个 tool 调用的完整清单与精确次数"无法独立核验**，本报告里凡属重建（例如 Gradle 运行次数、失败调用总数）都显式标注为**重建值**。
- 无设备、无真实付费调用；本报告不评估功能正确性，只评估工具使用。

## 2. 原子问题与裁决（40 条）

判定规则：确定性事实（计数、退出码、路径、差异）由我在代码里裁定；只有"语义支持关系"才交给 Jev，且不把计数类问题送 Jev。

### A. 读取与检索（12）

| # | 原子断言 | 裁决 | 依据 |
| --- | --- | --- | --- |
| A1 | 首次就整份 `read` 662 行的当天记录是最省方式 | **不必要（范围过大）** | AGENTS 要求读最新记录（必要），但无 offset/limit 导致输出被截断且 76% 截断来自 read；Jev 判 contradicts 0.96 |
| A2 | 整份读 `docs/development/README.md`（43 行） | 必要（廉价） | 该文件定义记录契约，必须整读 |
| A3 | 整份读 `PlaybackPresenter.java`（1,323 行） | 必要但昂贵 | 编辑守卫要求整读；但插入点检索本可先 grep 再 offset 读，只在编辑前整读一次 |
| A4 | 分片读 `AiSubtitleSessionBinderTest` 头/尾、`SubtitleExportBundle` 偏移段 | **正确做法** | 目的明确（锚点 + 结构），无截断 |
| A5 | 反复读当天记录尾部 4 次 | 部分不必要 | 记录持续增长确需重读，但锚点文本可在首次读取后保留，后续用 grep 行号定位 |
| A6 | 两次 `glob common/.../other/*.java` | 第二次不必要 | 同一会话内该目录无新增/删除时列表可复用 |
| A7 | `glob docs/plans/*.md` 选文件 | 必要（廉价） | 决定后续读取范围 |
| A8 | 用 `grep` 看锚点后凭缩进猜 `old_string` 编辑 zh strings | **错误** | 实测中文文件用 2 空格、英文 4 空格；应 read 精确字节 |
| A9 | `grep "mTimeline\b"` 找重构遗留引用 | 必要（高效） | 一次调用直接定位 6 处遗留，决定编译成败 |
| A10 | `grep "implements SubtitleDisplay"` 枚举测试假实现 | 必要（决策级） | 结果直接决定用 `default` 方法避免改 5 个文件 |
| A11 | 读 `MessageHelpers` 两份 checkout | **不必要** | glob 返回两个路径，第二份内容相同（162 行逐行一致），只需一份 |
| A12 | 用 `read` 读 `SubtitlePrefetchTickerTest` 头 50 行 | 正确 | 为新增测试复用既有 fixture |

### B. 写入、编辑与验证顺序（12）

| # | 原子断言 | 裁决 | 依据 |
| --- | --- | --- | --- |
| B1 | 在混合换行文件上直接用 `edit` | **错误（根因）** | 未知 EOL 就写，导致整文件被重写为单一行尾、89 行假改动 |
| B2 | 自建 `tmp/normalize-line-endings.py` 做行尾归一 | 必要补救，但本可避免 | 若先 `git ls-files --eol` 判定并用 LF 写新增行，就不需要这个脚本与后续 8 次调用 |
| B3 | 对 blob 为 LF-only 的文件运行归一化（5 个） | **完全没必要** | 脚本自报无变化；Jev contradicts 0.98 |
| B4 | 归一化重写后未重读就 `edit`（PlaybackPresenter） | **错误** | `file changed since it was read`；Jev supports 0.93 |
| B5 | 写 `tmp/commit-msg.txt`（前会话已存在） | **错误** | `file has not been read`；应换名或先读 |
| B6 | `present` 传 10 个文件 | **错误** | 上限 8；应分两批 |
| B7 | 三次 run_code 字符串转义失败 | **错误（重复同类）** | 双引号包中文词、模板字符串内嵌反引号各一次以上；每次重发整程序 |
| B8 | Gradle 输出只过滤 BUILD 状态行 | **错误** | 隐藏 javac 诊断，被迫再跑一次；Jev supports 0.90 |
| B9 | 用 `Select-String "BUILD"` 过滤 | **错误** | 命中 `preBuild` 等任务名噪声，输出不可判读 |
| B10 | 每 2–3 处编辑即跑一次编译 | 部分可省（重建：Gradle 约 12–16 次） | 本项目要求"改完即验"，但波次可更大；5 次编译失败各自花费一次运行 |
| B11 | 每批写入后 `git diff --check` | **正确（保留）** | 直接抓到行尾噪声与假改动 |
| B12 | 文档检查脚本按改动集运行 | **正确（保留）** | `PASS: n files` 是最低成本的一致性证据 |

### C. 构建、测试与门禁（8）

| # | 原子断言 | 裁决 | 依据 |
| --- | --- | --- | --- |
| C1 | 本地跑范围内测试 + 整模块测试 | 必要 | 与 CI 必需门禁同构（68 suites/558 tests 本地与 CI 数字一致） |
| C2 | 修 3 个缺陷后重跑整模块 | 必要 | 3 个缺陷跨 binder/presenter/segmenter，必须整范围回归 |
| C3 | 本地跑两模块 release lint | 必要 | 与 CI publish job 同构，提前暴露 minSdk/lint |
| C4 | 本地跑 app 编译 | 必要 | 覆盖 presenter 接线 |
| C5 | 用 JDK 11 + 仓库 wrapper | **正确（保留）** | 与 CI 测试 job 一致；JDK 17 下 Robolectric 不可用 |
| C6 | Gradle 放后台 + `job_output(wait)` | **正确（保留）** | 不空转、可在等待中包含检查 |
| C7 | 本地**不**跑 `assembleStbetaDebug`/apksigner | **正确取舍** | 由 GitHub-only 管线组装与校验，本地只做编译/lint/测试 |
| C8 | 本地不跑设备/真实网络 | 正确（无设备、无授权） | 与计划 §15 一致 |

### D. Jev 与证据使用（4）

| # | 原子断言 | 裁决 | 依据 |
| --- | --- | --- | --- |
| D1 | K4/K5 实施与自查期间 0 次 Jev，且未记录理由 | **流程缺口** | 确定性调试不发 Jev 符合 skill；但 AGENTS 要求主动整合且应记录不用的理由 |
| D2 | 本轮审计用 1 次 Jev audit / 12 条独立 claim | **正确用法** | 一次批量、每行自带证据、无计数类问题 |
| D3 | 未把设备日志/凭据/字幕正文发给 Jev | **正确** | 输入只有仓库路径、日志片段与命令输出 |
| D4 | 保存 input/result + 逐条裁决 | **正确（保留）** | 见 §3 与本目录 evidence |

### E. 记录、评审与发布（4）

| # | 原子断言 | 裁决 | 依据 |
| --- | --- | --- | --- |
| E1 | 当天记录 7 次更新、其中 1 次显式修正旧条目 | **正确** | 旧断言（断句未实现）被标注为基线时点状态而非删除 |
| E2 | 计划书分 6 次编辑 | 部分可省 | 每次编辑迫使整文件重读；可把同轮改动合并为一次 |
| E3 | 实施状态文件先建后改 4 次 | 部分可省 | 同上；若在候选数字确定后一次写齐可省 3 次读+改 |
| E4 | 交付流程（提交 → push → CI → 独立复核 → prerelease → 发布页补验收范围） | **正确（保留）** | 两轮候选的哈希/证书/门禁都被独立复核过 |
| E5 | `git add -A` 暂存 | 本会话安全但**风险实践** | Jev supports（工作树只有本任务改动）；共享工作区里应显式列路径 |

## 3. Jev 批次（live，实际调用）

- 调用：1 次 `audit`，模型 `typesafe/jev-1.13-20260917`，**4,824 input / 540 output tokens**，1,100.86 ms，cost 0.000202608，`status=judged`，`calls=1`，**无重试、无服务失败回退**。
- 输入文件 7,274 字节（上限 20,000），**文件 sha256** `a28dc70509dbe3671f0c0d62a97bc3b401df1a72f46f9a1555f5a0a5c7b69515`；脚本上报的规范化 state sha256 为 `89388a5a84b3cdfe9f6a997486839e1c0ec8eb2fb6aed956ebec24a02eefeaa2`（两者不同，前者是磁盘副本、后者是发送态）。
- 原始输入/结果：[输入](evidence/agent-tool-use-audit-2026-09-20-jev-input.json)、[结果](evidence/agent-tool-use-audit-2026-09-20-jev-result.json)。

| 行 | answer | winner | confidence | 本地阈值内？ | 人工裁决 |
| --- | --- | --- | --- | --- | --- |
| read-full-dailyrecord | contradicts | 0.96 | 0.94 | 是 | 采纳：整文件读不是最省 |
| normalizer-noop | contradicts | 0.98 | 0.97 | 是 | 采纳：空跑无信息 |
| edit-without-reread | supports | 0.93 | 0.88 | 是 | 采纳 |
| gradle-filter-hides | supports | 0.90 | 0.85 | 是 | 采纳 |
| add-all-staging | supports | 0.93 | 0.89 | 是 | 采纳（限定于本会话）；仍记为风险实践 |
| present-limit | supports | 0.97 | 0.97 | 是 | 采纳 |
| ts-escape-errors | supports | 0.88 | 0.81 | 是 | 采纳 |
| read-before-edit-required | supports | 0.98 | 0.97 | 是 | 采纳：整读是 harness 契约，不能算"自由浪费" |
| **selfcheck-test-first** | insufficient | 0.63 | 0.44 | **否 → 回源** | **降级**：证据只能支持"缺陷先被失败测试复现、修复后同一测试通过"，不能独立证明"先写测试再写修复"的时间顺序（该顺序只来自我的叙述） |
| no-jev-during-impl | supports | 0.90 | 0.86 | 是 | 采纳为流程缺口 |
| placeholders-not-claims | supports | 0.98 | 0.96 | 是 | 采纳：未预填未产生的数字 |
| spill-concentration | supports | 0.94 | 0.91 | 是 | 采纳：截断集中在 read |

`not_checked`（Jev 自陈边界）：evidence authenticity/freshness、code correctness、overall task completion —— 本报告不据此宣称任何完成度。

## 4. "完全没有必要"的 tool use（合并同类）

1. **对 LF-only 文件运行行尾归一化 5 次**（B3）：零信息、零收益。*替代*：先 `git ls-files --eol`，只处理 `w/crlf|mixed` 的文件。
2. **重复 `glob` 同一目录**（A6）与**读取同一份共享源码的另一份 checkout**（A11）：结果可复用。
3. **整文件 `read` 只为了少量事实**：A1（当天记录）、A3（PlaybackPresenter 第一次）——前者可由"读结构 + 最新小节 + grep"满足，后者可先 grep 定位插入点。
4. **因过滤器写错而多跑的 Gradle 2 次**（B8、B9）：一次运行就能拿到诊断。
5. **`present` 超限重试**（B6）、**写已存在文件被拒**（B5）：各 1 次可避免调用。
6. **三次转义失败重发整程序**（B7）：每次数百行程序被重新发送。

## 5. "错误"的 tool use（根因与改正）

1. **未知行尾就编辑**（B1、B2）——根因：把"编辑"当成纯文本操作，忽略了 index blob 的换行形态。影响：89 行假改动、1 次编辑失败、8 次归一化调用。改正：编辑前判定 EOL，新增行统一 LF，绝不整文件重写行尾。
2. **写后不重读就再改**（B4）——根因：把"我自己刚跑过脚本"当成"文件没变"。改正：任何非 `edit/write` 工具的写入（脚本、生成的日志、格式化）之后，先 read 再 edit。
3. **凭记忆构造 `old_string`**（A8 与两次未命中）——根因：用 grep/trim 后的文本猜原字节（缩进、中文引号形态）。改正：`old_string` 必须从"刚刚 read 到的原文"复制。
4. **输出过滤掩盖失败细节**（B8、B9）——根因：为了缩短输出而把诊断也过滤掉。改正：失败时把完整日志落盘后用精确模式（`\.java:\d+`）读回，禁止只打印状态行。
5. **`run_code` 内嵌引号/反引号未转义**（B7）——根因：把中文正文与代码片段直接放进 JS 字面量。改正：正文用「」/U+201C，模板字符串里不出现反引号（全部走变量拼接），发送前自检。
6. **`git add -A` 泛暂存**（E5）——本会话无后果，但在共享工作区是错误默认。改正：显式列路径或 `git add <dir>`。
7. **`job_output(wait)` 超出 run_code 默认 120s**（1 次）——改正：长等待要么缩短 timeout、要么用后台 + 轮询。
8. **5 次编译失败（代码错误，但由过小的验证波次放大）**——不是 tool use 语义错误，但每次都要一次独立 Gradle。改正：同模块同批编辑集中验证。

## 6. 值得保留的做法

- 后台 Gradle + `job_output(wait)`，等待期间做文档/读取类工作。
- 先写失败测试复现、再修代码、再整范围回归（本轮 3 个缺陷正是这样被抓到）。
- `git diff --check` + 文档检查脚本作为每批写入的固定收尾。
- Jev 一次批量、每行独立证据、保存输入/结果、逐条裁决；不把计数/门禁类问题交 Jev。
- 记录里区分"用户反馈 / 我复核 / 未验证"，并在被取代时显式标注而非删除。
- 交付路径：GitHub-only 门禁 + 本机独立复核（哈希/证书/badging）+ 发布页补验收范围。

## 7. 量化（可核验 vs 重建）

| 指标 | 值 | 来源 |
| --- | --- | --- |
| 被截断的模型可见输出 | 29 文件 / 1,555KB（read 76%） | spill 目录（可核验） |
| 本会话提交 | 9 commits / 69 files / +6,391 −171 | `git diff --shortstat`（可核验） |
| Jev | 1 次 / 12 checks / 4,824+540 tokens / 无重试 | 原始结果 JSON（可核验） |
| 失败的 tool 调用 | 约 12–14 次（edit 守卫 2、写文件 1、present 1、TS 解析 3、old_string 2、run_code 超时 1、编译失败 5） | **重建**（transcript 未落盘） |
| Gradle 运行 | 约 12–16 次 | **重建**（日志被覆盖） |
| 本会话 `tmp/` 草稿日志 | 约 0.8MB（gitignore） | `tmp/*.log`（可核验） |

## 8. 建议的预防规则（待用户决定是否写入项目规则）

1. **大文件读取**：先 `grep` 定位行号，再 `offset/limit` 读；只在编辑前整读一次；进度/报告类文件只读结构 + 最新小节。
2. **换行前置判断**：编辑前 `git ls-files --eol <file>`；新增行一律 LF；禁止因编辑导致整文件行尾重写。
3. **脚本写入后必重读**：任何非 edit/write 工具的写入之后，先 read 再 edit（否则稳定触发 `file changed since it was read`）。
4. **字符串构造规范**：run_code 里中文/代码正文用「」或 U+201C，模板字符串内禁止反引号；发送前自检一次。
5. **验证波次与日志**：同模块同批编辑合并验证；失败必须读回完整诊断（禁止只过滤状态行）。
6. **记录 Jev 决策**：每个实施轮显式写"用/不用 Jev 及理由"。

是否把 1–6 写进 `AGENTS.md` 或技能文件，属于机制变更，本报告只提建议、不擅自修改。

## 9. 本报告不主张什么

- 不主张"功能正确"或"任务完成"：Jev 的 `not_checked` 明确不含代码正确性；本报告只审工具使用。
- 不主张精确的调用总数与 Gradle 次数：transcript 未落盘，相关数字为重建并已标注。
- 不主张效率提升幅度：本报告与 Jev 都未做前后对照实验。
