# AI 字幕 RC 交付记录（2026-09-20）

本文件是**本次交付的唯一权威索引**：候选从哪里下载、怎么校验、对应哪个提交与运行、以及
“验收通过后如何把同一 APK 原样发布 stable”。使用与验收步骤见
[`docs/ai-subtitle-user-guide.md`](../../ai-subtitle-user-guide.md)。

## 0. 验收结果：**未通过**（2026-09-20 12:47–12:49，nightly-18）

用户在 TCL/Android 11 上实测第二轮候选后报告：不开 AI 与开了 AI 都导不出字幕、找不到填写 API Key 的地方、
AI 区功能无法验证。四份诊断报告显示：导出时刻**没有任何已绑定的字幕来源**（`sourceBound=false`）、
整个会话**从未发起时间轴抓取**（`snapshotStatus=NOT_REQUESTED`、0 条 `TIMELINE_REQUESTED`），
而诊断导出与写文件正常。直接原因与缺陷清单、待审查的修复方案见
[`subtitle-ai-tv-acceptance-round2-debug-2026-09-20.md`](subtitle-ai-tv-acceptance-round2-debug-2026-09-20.md)
与计划 §20。**本节的候选（nightly-16/18）仅作为缺陷证据保留，不得视为可接受的验收版本。**

## 1. 交付形态与结论

- 交付的是**用于验收的 GitHub prerelease 候选**，不是正式 release；正式 stable 发布等用户验收通过并授权后执行。
- 候选签名身份取决于仓库 Secrets：
  - **有** 4 个签名 Secrets（`SIGNING_KEY` / `KEY_STORE_PASSWORD` / `ALIAS` / `KEY_PASSWORD`）时，
    `release_mode` 通道产出**项目签名**的 release 候选（`versionName` 无后缀、`versionCode` 为预留稳定号），
    它才是可直接提升为 stable 的产物。
  - **没有** Secrets 时，自动推送路径只产出**debug 签名回退**的开发候选，并在 release 说明中明确标注；
    该签名在不同 runner 上会变化（本轮实测两个候选的证书指纹不同），**不能**作为长期升级身份或最终 stable 产物。
- 本仓库当前**没有任何 Secrets**（`gh secret list` 为空），因此本轮交付的是 debug 回退候选；
  这一点在第 4 节“未完成项”中单列，不得读作“成品已完成”。

## 2. 候选识别（tag / commit / run）

| 项目 | 值 |
| --- | --- |
| 业务代码提交 | `2f64f8a8`（N7 屏保用例修正；其前为 `7313b009` N2/N5、`6d6690df` N1 与 CI/版本策略、`e8720428` 承接上一会话文档） |
| 最终候选提交 | 本文件所在的文档提交（仅文档变更，代码与 `2f64f8a8` 相同） |
| 验收候选 prerelease | 见“第 3 节 本次验收候选” |
| CI 工作流 | `.github/workflows/CI.yml`：`tests`（JDK 11，必需范围 `com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*`）+ `publish`（JDK 17：lint → assemble → apksigner → prerelease） |

## 3. 本次验收候选

| 项目 | 值 |
| --- | --- |
| tag | `stbeta-32.53-nightly-16-16-debug` |
| 链接 | https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-16-16-debug |
| commit | `2f64f8a801e35bd4dfd78aa3b1f2da88de3bf34f`（含全部代码改动：N1/N2/N5/N7 + CI release 通道 + 版本策略） |
| workflow run | https://github.com/CometDash77/SmartTube-AI/actions/runs/35488592128（conclusion **success**，6m31s） |
| APK 资产 | universal / arm64-v8a / armeabi-v7a / x86 + `SHA256SUMS.txt` |
| universal SHA-256 | `93019ae4a9c030c88b184e2f7bf09d17121a6d8e3c730e29a61ed4c0f80e7a16`（45,075,xxx B 级、已与本机下载件逐字节核对一致） |
| arm64-v8a SHA-256 | `e6f64f218b22a44310b5acf2499ce398e9459d84e66a4492fa60ea45c5b3e55b` |
| armeabi-v7a SHA-256 | `d5c193962170a89c1822aaa42a5fc97c1fad6719860af27891638a770f9f2d62` |
| x86 SHA-256 | `4072b1c1747132bf14ea983b8b2b982a4a777fa88ea98484b8e968145bea8729` |
| 包名 / minSdk / targetSdk | `org.smarttube.beta` / 17 / 34（`aapt dump badging` 实测） |
| versionCode / versionName | `2443`（= 预留稳定号 2444 - 1）/ `32.53-nightly-16`（实测） |
| ABI（universal） | `arm64-v8a` + `armeabi-v7a`（universal 不含 x86，按实测 native-code 记录） |
| 签名 | **debug 回退**：`apksigner verify --verbose --print-certs` 输出 `Verifies`（v1+v2），证书 SHA-256 `57209913a4bcb18ee1b7071bf7d21a17fe712a8ead5e7468fc16109eeb07eca9`（与 nightly-15 的 `c26c1da0…` 不同 → debug 身份跨运行不确定） |
| 校验方式 | `sha256sum -c SHA256SUMS.txt`，或与本表哈希逐项比对 |

说明：候选提交之后只有**文档、注释与工作流**提交（包含把整模块测试从 informational 升为必需门禁、
以及让 `push` 忽略纯 Markdown 变更）。这些提交会再触发**同代码**构建（`stbeta-32.53-nightly-17-…`、
`stbeta-32.53-nightly-18-…`）；它们只用于验证 CI 改动本身，**不用于验收**，验收一律使用上表的
`stbeta-32.53-nightly-16-16-debug`。

已由 CI 验证的门禁改动：run https://github.com/CometDash77/SmartTube-AI/actions/runs/35488992138
（commit `f62bf227`）conclusion **success**，其中 `Full common module suite` 作为**必需步骤**通过 —— 整模块
`:common:testStbetaDebugUnitTest` 不再被 `continue-on-error` 隐藏。

## 4. 未完成项（阻断“成品完成”的准确缺项）

1. **项目签名 RC 未产出**：需要上述 4 个 Secrets。注入方式（由仓库管理员执行，密钥材料不入库）：
   把 keystore 以 base64 存入 `SIGNING_KEY`，并设置 `KEY_STORE_PASSWORD` / `ALIAS` / `KEY_PASSWORD`；
   然后 `gh workflow run CI.yml -f release_mode=true`。该路径缺 Secrets 会**直接失败**，不会退回 debug。
   该步骤同时需要决定并永久保存项目签名身份（丢失后所有旧版本无法被覆盖升级）。
2. **同签名覆盖升级与全新安装未验证**：需要稳定签名身份与一台可用的验收设备。
3. **真实服务联调未完成**：未使用真实 Key 发起付费调用；错误分类（401/403/402/429/协议）只在 fake 与本机合成服务上验证。
4. **真实 Android Keystore / 备份导出未验证**；API 17–22 的内存 Key 分支没有低版本设备证据。
5. **设备侧导出与交互验收未完成**：译文/双语/部分失败/中断导出、权限拒绝、空间不足、遥控器焦点、导出期间播放连续性。
6. **来源矩阵未完成**：手工字幕 / ASR / DASH / SABR / 合并源只完成了 ASR 一例（T13 原文导出）。

## 5. 验收通过后如何原样发布 stable

1. 用户按 [使用说明 §7](../../ai-subtitle-user-guide.md) 在电视上验收**本文件第 3 节**的候选，并回报结果。
2. 若候选是**项目签名 RC**：`gh release edit <tag> --prerelease=false`（或把同一批资产作为正式 release 发布），
   APK 字节不变，无需重新开发或打包。
3. 若候选是 **debug 回退**：先完成第 4 节第 1 项，再用 `release_mode=true` 产出项目签名 RC，
   对该 RC 重新执行验收清单（签名身份变化会改变安装/升级前提），然后按上一条提升为 stable。
4. 发布后核对远端资产哈希与链接、更新 `docs/development/2026-09-20.md` 与本文件的当前摘要；
   历史证据保留，不覆盖旧候选的 tag 与哈希。

## 6. 本轮 CI 证据（代码提交）

| commit | run | 结果 |
| --- | --- | --- |
| `6d6690df`（N1 + CI 通道/版本策略） | 35488107505 | 必需测试范围 **失败**（408 tests / 4 failed，全部是我新用例的假设错误：主线程队列顺序与断言前未执行 worker）；生产逻辑未改，已在 `7313b009` 修正 |
| `7313b009`（N2/N5） | 35488380586 | **success**：必需测试范围通过；两模块 `lintStbetaRelease`、`:smarttubetv:assembleStbetaDebug`、`apksigner verify` 通过并发布 `stbeta-32.53-nightly-15-15-debug` |
| `2f64f8a8`（N7 屏保用例修正） | （与最终候选同表记录） | 见第 3 节 |

本地对 `stbeta-32.53-nightly-15-15-debug` 的独立复核（作为流水线机制验证）：
universal APK SHA-256 `a2f615de90195d1a79f30f7dfd03bd85b4161af91160e70e6dc700e02ced2a05` 与 `SHA256SUMS.txt` 一致；
`aapt dump badging` 显示 `versionCode 2443` / `versionName 32.53-nightly-15` / `sdkVersion 17` / `targetSdkVersion 34` /
universal 含 `arm64-v8a`+`armeabi-v7a`；`apksigner verify --verbose --print-certs` 输出 `Verifies`（v1+v2），
证书为 runner 的 debug 证书（SHA-256 `c26c1da0…`，与更早候选的 `9e80731d…` 不同，印证 debug 身份不可跨运行复用）。
