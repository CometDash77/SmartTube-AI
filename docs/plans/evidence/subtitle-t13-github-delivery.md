# T13 GitHub 远端编译、Action 运行与 prerelease 交付 —— 完整操作记录

Date: 2026-09-20, Asia/Hong_Kong. 分支 `production`，远端 `origin` = `https://github.com/CometDash77/SmartTube-AI.git`（fork，默认分支 `master`）。
用户在本阶段明确授权“在远端 GitHub 编译并发布 prerelease 的 APK（至少 universal），由用户下载后手动验证”，因此本文件记录的 commit/push 与 Actions 触发均属授权范围。
本文件不记录任何密钥、token 或 keystore 内容；签名证书只记录公开指纹。

## 1. 推送的提交（按时间顺序）

| 提交 | 说明 | 变更规模 | 推送区间 |
| --- | --- | --- | --- |
| `dc481477` | T13 电视本地一键导出字幕与诊断日志 + GitHub 构建与 prerelease 工作流 | 28 files, +3387/-85 | `33e0fd2f..dc481477` |
| `a0a1ae60` | CI：测试/lint/构建/签名校验/prerelease 一条龙；无签名 Secrets 时发布 debug 回退候选 | 2 files, +55/-33（`.github/workflows/CI.yml`、`.gitignore`） | `dc481477..a0a1ae60` |
| `af4eddca` | CI：修正 NOTES.md 中代码围栏的引号（反引号在双引号内会被当成命令替换） | 1 file, 4 行 | `a0a1ae60..af4eddca` |
| `648a0d3b` | docs：记录 T13 的 GitHub 验证结果与首个 prerelease（含 debug 回退说明） | 3 files, +48/-2 | `af4eddca..648a0d3b` |
| `432f652d` | docs：记录发布 APK 的独立校验（哈希/包名/版本/ABI/签名） | 1 file, +1 | `648a0d3b..432f652d` |

未提交/未推送的内容：工作区仍有其他并行会话修改的 `AGENTS.md`、`.agents/skills/smarttube-deepseek-ptc/SKILL.md`、`.agents/skills/smarttube-jev-triage/SKILL.md`、`docs/development/review-handoff.md`（本任务未改动、未纳入提交）。子模块指针未变（`SharedModules` 13f5687d；`MediaServiceCore` 保留既有 recorded-gitlink 漂移）。

## 2. 远端状态核查（执行前）

| 命令 | 观察结果 |
| --- | --- |
| `gh secret list`（未带 -R） | 失败：`multiple remotes detected`（origin + upstream），后续全部命令都显式使用 `-R CometDash77/SmartTube-AI` |
| `gh run list`（未带 -R） | 显示的是 **upstream** 仓库的历史 run（2026-09-18/19 的 “Build Debug APK”、cleanup），不是本 fork；这是一次误读，已纠正 |
| `gh api repos/…` | `default_branch=master`、`fork=true` |
| `gh api repos/…/actions/permissions` | `{"enabled":true,"allowed_actions":"all","sha_pinning_required":false}` |
| `gh api repos/…/actions/secrets` | `{"total_count":0,"secrets":[]}` —— **仓库没有任何 Secrets**（SIGNING_KEY / KEY_STORE_PASSWORD / ALIAS / KEY_PASSWORD 全部缺失） |
| `gh api repos/…/actions/runs?per_page=5` | `total_count=0`（推送后仍为 0） |
| `gh api repos/…/commits/dc481477/check-runs` | `total_count=0` |
| `gh api repos/…/actions/workflows`（默认分支视角） | 4 个工作流：`CI.yml`(362097876)、`cleanup.yml`(362097877)、`stale.yml`(362097878)、`virustotal_scan.yml`(362097879) |

## 3. 触发与运行

| 动作 | 结果 |
| --- | --- |
| `git push origin production`（dc481477） | 推送成功；**未产生任何 run**（run 数为 0，check-run 为 0）。该 fork 的普通 push 不触发 workflow，不是 workflow 定义问题 |
| `gh workflow run CI.yml -R … --ref production`（当时 HEAD = dc481477） | 创建 run **35483718558**；该次使用的是“签名缺失即整任务失败”的旧版工作流，被后续回退版本取代，已用 `gh run cancel 35483718558` 取消（避免红叉与浪费额度） |
| 推送 a0a1ae60 后 `gh workflow run CI.yml --ref production` | 创建 run **35483822239**（headSha=a0a1ae60f2f1b15bd8bd6e81d155e17bad213375），最终结论 **success** |

run 35483822239（https://github.com/CometDash77/SmartTube-AI/actions/runs/35483822239）的两个 job：

| job | id | 关键输出 |
| --- | --- | --- |
| `Unit tests (JDK 11, subtitle/export scope)` | 106006353585 | `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.exoplayer.other.*"` → **BUILD SUCCESSFUL in 2m17s**（348 actionable tasks）；随后的整模块基线步骤（`continue-on-error: true`）→ `398 tests completed, 2 failed`，两项为既有 `com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManagerTest`（`motherActivityPauseReleasesSuppression`、`pausedInstanceDoesNotAffectActiveInstanceOrStrandRegistryLock`），该步骤未阻断 job，job 结论 success |
| `Lint, build, verify and publish prerelease (JDK 17)` | 106006715045 | `:common:lintStbetaRelease :smarttubetv:lintStbetaRelease` → **BUILD SUCCESSFUL in 2m31s**；`:smarttubetv:assembleStbetaDebug` → **BUILD SUCCESSFUL in 1m48s**；`apksigner verify --verbose --print-certs` 对 4 个 APK 输出 `Verifies`，V2 Signer certificate SHA-256 `9e80731d74ee74b46e2c8a0ee7b7e32b7271ee8031f313db7087ffd4c49e73ab`；`gh release create` 发布 prerelease |

## 4. 发布的 prerelease

- tag：`stbeta-32.53-nightly-2-2-debug`，`prerelease=true`，target commit `a0a1ae60f2f1b15bd8bd6e81d155e17bad213375`
- 链接：https://github.com/CometDash77/SmartTube-AI/releases/tag/stbeta-32.53-nightly-2-2-debug
- 资产（4 APK + 清单）：

| 资产 | 字节 | SHA-256 |
| --- | --- | --- |
| `SmartTube_beta_32.53-nightly-2_universal.apk` | 45,050,196 | `5f63c4145e9119707019271486f2a7ccc4e09c19a5d6bb1b373a6bf6e5b1005e` |
| `SmartTube_beta_32.53-nightly-2_arm64-v8a.apk` | 33,550,463 | `c78d2fbadee75e72f72fd2948bee72e76ee6f40b950cd2bdba8b18a0c07cc546` |
| `SmartTube_beta_32.53-nightly-2_armeabi-v7a.apk` | 31,170,583 | `f09e63da109fa556294ee79e37345f42fcc9b6b0816b4727747d6216e92bb031` |
| `SmartTube_beta_32.53-nightly-2_x86.apk` | 34,488,760 | `0819a784aaabd58fdef631361c33fab29e3938f39a69734d945bdd7bafc9547e` |
| `SHA256SUMS.txt` | 612 | —— |

签名身份：**debug 回退**。仓库无 Secrets，按计划 §15 不得发布未签名的 release 包、也不得生成临时密钥掩盖，因此工作流构建 `stbetaDebug` 并在 release 说明首部标注：该 APK 由 runner 上的 AGP debug keystore 签名、无法覆盖另一签名下的同包名已安装版本、且下一次回退运行使用不同的 debug 密钥。正规路径需配置四个 Secrets。

## 5. 发布产物的独立复核（本地，非 CI 自证）

下载 universal 资产后用本机 SDK build-tools 37.0.0 复核：

- 字节 45,050,196，SHA-256 `5f63c414…1005e`，与 `SHA256SUMS.txt` 完全一致。
- `aapt dump badging`：`package: name='org.smarttube.beta' versionCode='2443' versionName='32.53-nightly-2'`，`sdkVersion:'17'`，`targetSdkVersion:'34'`，`native-code: 'arm64-v8a' 'armeabi-v7a'`（universal 包不含 x86），`application-label:'SmartTube beta'`。
- `apksigner verify --verbose --print-certs`：`Verifies`；v1(JAR) true、v2 true、v3/v3.1/v3.2/v4 false；Number of signers: 1；certificate DN `C=US, O=Android, CN=Android Debug`；certificate SHA-256 `9e80731d74ee74b46e2c8a0ee7b7e32b7271ee8031f313db7087ffd4c49e73ab`（与 CI 日志一致）；RSA 2048。
- 环境注意：本机 `JAVA_HOME` 指向不存在的 Android Studio JBR，必须临时覆盖为 Gradle 使用的 JBR 17（`~/.gradle/jdks/jetbrains_s_r_o_-17-amd64-windows.2`）才能运行 `apksigner`。
- 复核完成后删除了临时下载（`tmp/t13-apk`）；本地未创建 keystore 或密码文件。

## 6. 本次过程中发现并修正的自身缺陷

1. **签名缺失会整任务失败**（首版工作流）：改为“有 Secrets → 签名 release；无 Secrets → 明确标注的 debug 回退”，既满足 §15 不发布未签名 release 的要求，又让用户拿到可安装的验证包。
2. **release 说明里的 APK 元数据块为空**：notes 生成脚本使用 `echo "```"`，而 bash 会把双引号内的反引号当作命令替换，导致 `aapt`/`apksigner` 的元数据段未写入；`af4eddca` 把四处改为单引号。已发布的候选不受影响（签名校验本身已通过），其元数据由本文件第 5 节补齐；下一次 dispatch 生成的说明将自带该段。

## 7. 后续会话必须知道的运维事实

- 该 fork 的 push 不触发 workflow：新交付需 `gh workflow run CI.yml -R CometDash77/SmartTube-AI --ref production`，或先在 Actions 页启用 workflow。
- `gh` 必须带 `-R CometDash77/SmartTube-AI`（本地同时存在 origin 与 upstream）。
- 仓库无 Secrets；配置 `SIGNING_KEY`（keystore base64）、`KEY_STORE_PASSWORD`、`ALIAS`、`KEY_PASSWORD` 后同一工作流会产出项目签名的 release 候选。
- versionCode 未调整（2443），与已安装的不同签名版本无法覆盖安装；未取得已安装签名证据前不承诺可覆盖升级。
- 唯一 tag 由 `stbeta-<versionName>-<run_number>[-debug]` 组成，不覆盖旧验收包；一个 run 只对应一个 commit。
- 设备验收仍未进行：两个导出按钮的遥控器焦点、`Documents/SmartTube/Exports/` 可见可复制、权限拒绝、空间不足、导出期间播放不中断。
