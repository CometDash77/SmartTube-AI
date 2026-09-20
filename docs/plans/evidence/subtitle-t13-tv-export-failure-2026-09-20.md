# T13 设备验收失败报告：「导出字幕」在自动字幕（ASR）视频上失败

Date: 2026-09-20, Asia/Hong_Kong. 验收对象：prerelease `stbeta-32.53-nightly-2-2-debug`（commit `a0a1ae60`）。
证据：用户从电视导出的两份诊断报告，已按字节迁移到本仓库（SHA-256 与用户附件一致）：

- `docs/plans/evidence/device-logs/2026-09-20/SmartTube-diagnostics-20260920-104921.txt`（1404 B，sha256 `37cebd5215af3a2c05d4049e3cb0754dc334f24a672bab93aa06d93de66da808`）
- `docs/plans/evidence/device-logs/2026-09-20/SmartTube-diagnostics-20260920-104938.txt`（2160 B，sha256 `532cce771e60d8a03849d075f21dc2eef0d48ab419b06272ebe051af332533df`）

## 1. 结论摘要

「导出字幕」失败**不是写文件失败**，而是导出的前置条件未满足：本次会话从未取得字幕时间轴
（`timelineFrames=0`、`snapshotStatus=CANCELLED`），因此导出按设计以 `NO_TIMELINE` 拒绝，绝不生成空文件。
时间轴缺失的原因是产品交互缺陷：**时间轴抓取只由 AI 翻译开关触发**，而用户开启 AI 后 1.766 秒就关闭了它，
`applyAiEnabled(false)` 随即取消了在途抓取。诊断导出在同一份会话里全部成功（`EXPORT_DIAGNOSTICS_OK`），
说明导出/存储链路本身正常。

## 2. 证据（两份日志的实测值）

设备与版本：`androidRelease=11` / `androidSdk=30` / `deviceModel=tc8000_ay30a2`（TCL）/ 应用 `32.53-nightly-2`（2443，`org.smarttube.beta`）。

来源绑定成功（T02 侧正常）：`sourceBound=true`、`sourceType=asr`、`sourceMime=text/vtt`、
`sourceLanguageCode=en`、`sourceVssId=a.en`、`sourceTranslatable=true` —— 自动字幕轨被正确识别，不是"没有字幕轨"。

时间轴与会话：`snapshotStatus=CANCELLED`、`timelineFrames=0`、`timelineItems=0`、
`timelineFingerprintPresent=false`、`cacheEntries=0`、`statsRequests=0`（无时间轴 → 预取器一次调用都没发起）。

事件重建（#1 与 #2 合并，偏移为日志启动后的毫秒）：

| 偏移 | 事件 | 含义 |
| --- | --- | --- |
| +86.754s | `SOURCE_CHANGED` | 媒体源变化，快照状态被重置 |
| +116.484 / +119.318 / +126.875s | `EXPORT_SUBTITLES_FAILED_NO_TIMELINE` ×3 | 用户连按三次「导出字幕」，都因无时间轴被拒绝 |
| +121.605s | `EXPORT_DIAGNOSTICS_OK` | 同一时刻「导出诊断日志」成功 → 存储与导出链路正常 |
| +130.698s | `AI_ENABLED` | 用户打开 AI 翻译开关 |
| +130.707s | `TIMELINE_REQUESTED` | 9 ms 后开始抓取字幕时间轴（唯一触发点就是这里） |
| +132.464s | `AI_DISABLED` | **1.766 秒后**关闭开关 |
| +137.767s | `SNAPSHOT_CANCELLED` | 在途抓取被取消，时间轴永远没有装进来 |
| +155.026s | `EXPORT_DIAGNOSTICS_OK` | **#2 报告**：诊断导出再次成功 |
| +157.646 … +168.808s | `EXPORT_SUBTITLES_FAILED_NO_TIMELINE` ×12 | 用户在约 11 秒内反复尝试导出字幕，全部失败 |

会话末状态：`aiEnabled=false`、`keyConfigured=false`、`displayMode=ORIGINAL_ONLY`、`targetLanguage=zh-Hans`。

## 3. 根因（代码定位）

1. **时间轴只在 AI 开关打开时抓取。** `PlaybackPresenter.requestAiSubtitleTimeline()` 的三个调用点全部被 AI 开关
   或"AI 已启用"守卫：`applyAiEnabled(true)`（`PlaybackPresenter.java:513`）、配置变更后重启
   （`:391`，条件为 `mAiSettings.getSettings().isEnabled()`）、换轨（`:867`，同样条件）。
   AI 关闭时没有任何路径会去取得时间轴。
2. **关闭 AI 会取消在途抓取。** `applyAiEnabled(false)` 调用 `cancelAiSubtitleTimeline()`（`:516`），
   该方法设置取消标志（`:483`），worker 侧随即返回 `CANCELLED`；日志中的 `SNAPSHOT_CANCELLED` 即由此产生。
   一次 ASR 载荷的下载+解码通常需要数秒（读取器上限：原始 2 MiB、20,000 事件、30 秒解码期限），1.766 秒不够。
3. **导出按设计拒绝而不是生成空包。** `SubtitleExportBundle.build()` 在时间轴为 null/空时返回
   `Failure.NO_TIMELINE`（`SubtitleExportBundle.java:86` 起），控制器映射为 `CODE_NO_TIMELINE`，
   UI 显示 `ai_subtitle_export_no_timeline`。这正是"不能生成伪成功空文件"的预期行为。
4. **给用户的提示与实际机制不符（可用性缺陷）。** 失败提示是"尚未取得字幕时间轴，请开启字幕后等待首次快照"
   （`values/strings.xml:45`）。但"开启字幕"并不会触发抓取——必须开启 **AI 翻译开关**并等它完成。
   对一个只想导出原文（原视频没有字幕、只想留一份 ASR 原文）的用户，这是错误的引导，而且他"没有 Key"这一事实
   与时间轴抓取无关（抓取不需要 Key），所以提示也没有说明"没有 Key 也能导出原文"。
5. **时间轴一旦装好会保留，关闭 AI 不影响已装好的时间轴**（`mAiSubtitleBinder` 只在引擎释放时置空，`:724`）。
   也就是说：只要让抓取跑完一次，之后即使关掉 AI 也能导出原文 —— 用户缺的只是"跑完一次"。
6. **不是缓存/计数器问题**：`cacheEntries=0`、`statsRequests=0` 是无时间轴的必然结果（预取器在没有时间轴时
   直接返回，`SubtitleTranslationDispatcher.tick()` 的 `mTimeline == null` 分支），不是失败原因。

## 4. 尚未证明的部分（重要，不要写成已解决）

本次日志**只能证明抓取被取消**，不能证明自动字幕（ASR）的载荷能被成功取得和解析。计划 §13.4 的未决风险仍在：
T03 曾实测直接请求 `api/timedtext` 返回 HTTP 200 但响应体为空，ASR 轨是否需要与播放相同的鉴权/cookie 未知。
因此存在第二种结局：即使让抓取跑满时间，也可能得到 `UNSUPPORTED_FORMAT`/`IO_FAILED`/`DECODE_FAILED`
（而不是 `OK`）。判别这两者只需要一次复测（见 §5）。

## 5. 判别性复测（请用户执行，用于定案）

最小步骤（不要中途关开关、不要暂停/换轨/退出播放器）：

1. 播放同一个视频，长按字幕键确认自动字幕轨处于选中状态（日志里 `sourceBound=true` 已满足）。
2. 打开 AI 翻译开关（本视频）。
3. **保持 20–30 秒不动**（不要按返回、不要关开关、不要快进）。
4. 按「导出字幕」。

预期与判读：

| 结果 | 判读 |
| --- | --- |
| 导出成功（ZIP 含 `original.srt`，译文为空但有说明） | 根因就是"抓取被取消 + 提示误导"，按 §6 的 A/C 修复即可 |
| 仍失败，且新日志 `snapshotStatus` 为 `UNSUPPORTED_FORMAT`/`IO_FAILED`/`DECODE_FAILED`/`TIMEOUT` | 属于 T03 未决的 ASR 获取/鉴权问题，需要另一条修复线（受控的鉴权来源读取），不能靠改交互解决 |
| 仍失败，`snapshotStatus` 仍为 `CANCELLED` | 说明还有别的取消路径在起作用（例如播放器事件/焦点变化），需要新的日志定位 |

新日志请一并附上（诊断导出本身是可用的）。

## 6. 修复方案选项（待确认后实施）

- **A（推荐，最小且直接）**：AI 开关关闭时**不再取消在途的时间轴抓取**（仅保留源变化/换轨/seek/引擎释放的取消）。
  依据：时间轴是本地字幕读取的结果，不是付费调用；取消它只会让"已经花掉的读取"白费并让导出永久性失败。
  影响面小（一处守卫），风险是关掉 AI 后仍会完成一次字幕读取。
- **B（可发现性）**：在字幕菜单提供一个不依赖 AI 开关的显式入口（例如"准备字幕时间轴/为导出准备"），
  或首次在"仅原文"模式下按「导出字幕」时提示"需要先准备时间轴"并提供一键准备。这样导出的"零副作用"属性保持不变，
  抓取由用户显式发起。
- **C（提示修正）**：把失败提示改成可操作的准确措辞，例如"未取得字幕时间轴：请开启 AI 翻译开关并保持
  20–30 秒（导出原文不需要 Key），或使用【准备字幕时间轴】"。同时说明"原视频无字幕时使用自动字幕轨"。
- **D（不改交互的兜底）**：导出失败时在诊断报告里增加"未取到时间轴"的明确计数与最后一次快照状态
  （`snapshotStatus` 已经具备，只需在提示里直接显示它）。不建议单独采用，因为它不解决"用户做不到"的问题。

推荐组合：**A + B + C**（A 解决根因，B 解决首次可发现性，C 解决误导），D 作为现有能力顺带在提示中展示。
若 §5 复测落到第二行（ASR 载荷本身取不到），则先做受控的鉴权来源读取，再回到 A/B/C。

## 7. 需要用户确认的问题与答复（2026-09-20）

答复：① 目录就用 `docs/plans/evidence/device-logs/2026-09-20/`；② **不提交、不上传**（用户原话："错误是留给生产环境判断的，不需要上传"）；③ **暂时无法复测**——设备上"AI 设置并不完全，没有方便设置 Key 的地方"，因此 §5 的复测步骤当前不可执行；④ 那 1.766 秒是用户**手动**关闭 AI 开关，确认了 `AI_DISABLED` → `SNAPSHOT_CANCELLED` 这条路径（不是界面自动跳回）。

这一组答复同时改变了对本缺陷严重性的判断：**用户既没有方便配置 Key 的入口，也不应该为了导出原文而先打开翻译开关**。因此修复必须让"无 Key、AI 关闭"也能导出原文，交互修复（§6 的 B）从"可发现性优化"上升为首要修复项。

### 原始问题清单（保留备查）

1. 迁移目标目录是否正确（现放在 `docs/plans/evidence/device-logs/2026-09-20/`），是否需要换到别处；
   是否需要把这两份日志 commit 并推送到远端。
2. 现场看到的失败提示文字与按钮名（中文/英文），以及当时 AI 开关是开还是关。
3. 那次 `AI_ENABLED`→`AI_DISABLED` 的 1.766 秒：是你手动关的，还是开关自己跳回去了？
4. 是否希望"完全不需要 AI 翻译也能导出原文"（即默认使用自动字幕轨的原文），还是可以接受
   "为了导出原文而先进一次 AI 开关"？
5. 是否愿意按 §5 复测并回传新日志（这是判定 ASR 载荷能否取得的唯一可靠证据）。


## 9. 结论变更（2026-09-20，依据用户答复）

1. **根因已确认**：不是写文件失败、不是没有字幕轨、不是缓存/计数器问题，而是"时间轴抓取只由 AI 开关触发 + 手动关闭 AI 取消了在途抓取"，随后导出按设计返回 `NO_TIMELINE`（15 次）。用户已确认那 1.766 秒是他手动关闭。
2. **ASR 载荷能否取得仍未证明**：设备侧无法复测（缺 Key 配置入口），所以这条只能靠新候选的行为来区分。为让下一次失败自带判别信息，修复时必须把 `snapshotStatus` 直接放进失败提示（例如"未取得字幕时间轴：上次快照结果 = CANCELLED/IO_FAILED/UNSUPPORTED_FORMAT…"），而不是让用户再去导一次诊断。
3. **首要修复变更为 B → A → C**（不再只是 A）：
   - **B（首要）**：提供不依赖 AI 翻译开关的时间轴准备能力——字幕菜单显式入口"准备字幕时间轴（导出原文用）"，或在字幕被显示/选中时自动准备；导出的"零副作用"属性保持不变（抓取由用户或字幕事件发起，不是导出副作用，也不重新抓取带鉴权的字幕 URL）。
   - **A**：`applyAiEnabled(false)` 不再取消在途的时间轴抓取（仅保留源变化/换轨/seek/引擎释放的取消），避免"只要关掉开关就永远导不出原文"。
   - **C**：修正失败提示，明确"导出原文不需要 Key、不需要打开 AI 翻译"，并直接显示上一次快照状态；原视频无字幕时说明使用自动字幕（ASR）轨。
4. **与本缺陷无关但阻塞翻译验收的独立问题**（T08/T09 范围，不在 T13 内修）：设备上缺少方便的 API Key 输入入口，用户无法完成 AI 翻译配置；这意味着即使时间轴准备好了，译文覆盖也只能在"无 Key"下保持 0（导出会正确标注缺失译文）。
5. **下一次验收的最小判定**：装上新候选后，在**AI 关闭、无 Key** 的情况下直接按「导出字幕」——若得到包含 `original.srt` + 说明文件的 ZIP，则 T13 的"导出原文"路径成立；若失败提示显示 `snapshotStatus` 为 `IO_FAILED`/`UNSUPPORTED_FORMAT` 等，则确认是 T03 未决的 ASR 获取/鉴权问题，需要单独一条修复线。
## 8. 现场可用性观察（附带）

- 按用户要求（"错误是留给生产环境判断的，不需要上传"），原始设备日志只保留在本地工作区，**不纳入提交**；本文件记录其文件名与 SHA-256，便于日后核对。
- 同一会话中「导出诊断日志」**连续两次成功**，且报告内容完整（字段白名单、事件码、来源类型/MIME 都对），
  说明诊断导出在 Android 11 / TCL 设备上工作正常，公共目录写入也没有报权限或空间问题。
- 用户在约 11 秒内连按 12 次「导出字幕」，每次都被正确去重（同一时刻只有一个任务）并各自返回 `NO_TIMELINE`；
  未出现重复任务或卡死，说明忙碌守卫与失败路径在真机上表现符合设计。

## 10. 修复实现（2026-09-20，按用户要求改为与 AI 完全解耦）

用户判定原方案（"先开 AI 准备时间轴，再导出"）不合理，明确要求：**导出字幕与 AI 解耦；原文、仅译文、原文+译文、
被中断的原文+译文、翻译失败的字幕都要能导出**。实现如下（本轮未推送，CI 候选待下一步）：

### 10.1 时间轴不再依赖 AI 开关

| 位置 | 改动 |
| --- | --- |
| `PlaybackPresenter.onVideoLoaded` | 视频加载后直接准备时间轴（原先只在 AI 打开时才准备） |
| `PlaybackPresenter.onTrackChanged` | 换轨后无条件准备（原条件为"AI 已启用"） |
| `PlaybackPresenter.onSourceChanged` | 媒体源替换后也准备新源的原文时间轴 |
| `PlaybackPresenter.onSeekEnd` | 已安装的时间轴跨越 seek 保留；仅当尚无时间轴时才重新准备（避免每次 seek 重复读取） |
| `PlaybackPresenter.applyAiEnabled(false)` | **不再取消在途快照**：关闭翻译不会丢掉原文时间轴（这是本次故障的直接原因） |
| `PlaybackPresenter.requestAiSubtitleTimeline` | 新增"该源已有时间轴就不重复抓取"的守卫，使上述调用点可以无条件调用而不增加读取 |
| `AiSubtitleSessionBinder.refreshSource` | 选中源变化时清空已安装时间轴，避免把上一个源的原文当作新源导出/预取 |

结果：**没有 Key、AI 关闭、从未打开过 AI 翻译的用户，也能导出原文**；时间轴由字幕事件在后台准备，导出按钮本身仍然
不发起任何抓取（保持"零副作用"）。

### 10.2 ZIP 覆盖全部翻译状态

归档条目（[SubtitleExportBundle.java](../..//common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/SubtitleExportBundle.java)）：

| 条目 | 内容 |
| --- | --- |
| `original.srt` | 原文（整片已取得的时间轴） |
| `translated.srt` | 仅译文；缺失处回退原文，保证任何一条都不为空 |
| `bilingual.srt` | 原文在上、译文在下；缺失处只有原文（"被中断的原文+译文"就是这种形态） |
| `untranslated.srt` | 只含**仍没有译文**的那些 cue（可播放），用来直接查看"没翻出来/还没翻到"的部分 |
| `translation-status.txt` | 逐条状态：`TRANSLATED` / `FAILED`（尝试过但无结果）/ `NOT_ATTEMPTED`（从未到达，即被中断），含时间码、原文一行、以及 total/translated/failed/notAttempted/coveragePercent 与导出时的 AI/Key/目标语言/快照状态 |
| `README.txt` | 覆盖说明（原有中英双语要点）+ 新增"本导出不需要 AI 开关或 API Key，翻译被中断或失败时也能导出" |

状态来源：`SubtitleTranslationCache.statusSnapshot()`（新增，纯逻辑）在**点击导出时**复制成快照的一部分，
与译文映射一样属于"点击时刻的固定副本"，因此导出过程中换视频/换轨不会污染结果。

### 10.3 失败提示

`NO_TIMELINE` 的提示改为直接显示**最近一次快照状态**并说明真实机制（三种语言）：
"尚未取得字幕时间轴（最近一次快照：%1$s）。原文会自动准备，请等待数秒后重试；导出原文不需要 Key，也不需要打开 AI 翻译。"
这样即使自动准备失败，一次点击就能区分 `CANCELLED`/`IO_FAILED`/`UNSUPPORTED_FORMAT` 等不同原因，不再需要额外导一次诊断。

### 10.4 本地验证（本轮）

- JDK 17 `javac` 编译通过：导出/诊断/写入/缓存/绑定 11 个类 exit 0；`PlaybackPresenter` 与 `PlayerUIController`
  在同一 classpath 下仅剩 15 条"尚未重新生成的 `R.string.ai_subtitle_export_*`"诊断，无其它错误。
- JUnitCore 直接执行：**OK (48 tests)**（原 45 + 新增 3 条：全部状态导出、缓存状态分类、状态副本）。
- 未运行：Gradle 任务与 lint（属 GitHub Actions）；设备验收。

### 10.5 仍待确认/权衡

1. **额外一次字幕读取**：解耦后，凡是"已选中字幕轨"的视频都会在后台准备一次时间轴，即使该用户从不导出、也不用 AI。
   这是满足"导出一按即用"的取舍。若希望零成本，可改为"仅在按下导出/打开字幕菜单时才准备"（把 10.1 的
   触发点从视频加载/换轨改为导出或菜单入口）。
2. **ASR 载荷能否取得**仍未证明（§4）；但现在配置正确时它会自动准备，失败原因会直接出现在提示里。
3. 尚未处理：**翻译会话的失败原因**只做到"逐条 FAILED/尝试过"，没有保留批次级原因（401/429 等）；如需按原因分类导出，
   需要在会话里保留最后一次停止原因。
