# SmartTube AI

基于 [yuliskov/SmartTube](https://github.com/yuliskov/SmartTube) 的独立社区 fork，为 Android TV 增加 AI 字幕翻译、智能上下文、规则断句和字幕导出。本项目不是上游官方发行版。

**[下载稳定版](https://github.com/CometDash77/SmartTube-AI/releases/latest)** · [所有版本与测试候选](https://github.com/CometDash77/SmartTube-AI/releases) · [AI 字幕使用说明](ai-subtitle-user-guide.md)

## 相比上游新增的功能

- **AI 字幕翻译**：原文、译文、双语三种显示模式；目标语言设置；关闭 AI 立即回到原生字幕。
- **智能上下文**：基础、连贯、视频增强三档，支持已验证译例与视频主题分析；失败时有降级策略。
- **规则断句**：把连续短字幕按时间边界合并，重叠字幕、说话人切换等特殊区域保留原帧。
- **强制重翻**：清理当前翻译结果并重新翻译，隔离旧请求结果，避免旧译文写回新会话。
- **字幕导出**：导出原文、译文、双语、未翻译条目和状态文件；规则断句结果另行保留。原文导出不需要 API Key，也不需要打开 AI。
- **AI 配置与状态**：掩码 Key 输入、保存/清除反馈、手动连接测试、字幕加载与翻译状态提示。
- **请求控制**：串行请求、预取预算、有界缓存、失败冷却和限流退避，切视频、字幕源或配置后拒绝过期结果。
- **独立发布与更新**：永久项目签名，应用内更新只跟随本仓库 stable release，不再拉取上游更新包。

## 上游来源与更新

直接 fork 自 **[yuliskov/SmartTube](https://github.com/yuliskov/SmartTube)**，本发布线整合上游 **32.54**（`4a786a8f`），包含恢复“不感兴趣”和“不推荐此频道”等最新改动，并保留本项目的 AI 功能。

上游原始介绍保存在 [README-upstream.md](README-upstream.md)。SmartTube 原有播放器、电视遥控器交互等能力归功于上游及其贡献者；本仓库保留原有许可证和版权声明。

[`upstream-mirror`](https://github.com/CometDash77/SmartTube-AI/tree/upstream-mirror) 是不含本项目改动的纯上游分支，每六小时自动同步。通过 [差异比较](https://github.com/CometDash77/SmartTube-AI/compare/master...upstream-mirror) 观察新更新；上游变化不会未经验证就自动合入 stable。

## 安装与升级

1. 从本仓库 [稳定版发布页](https://github.com/CometDash77/SmartTube-AI/releases/latest) 下载 APK。普通 ARM 电视可选 `universal`；也提供 `arm64-v8a`、`armeabi-v7a` 和 `x86` 包。
2. 本项目沿用 `org.smarttube.beta` 包名，电视显示名称为 **SmartTube AI**。若已安装上游同包名版本或旧 debug 候选，由于签名不同，需要先卸载；**卸载会清空应用数据和已保存的 Key**，请先保存需要的配置与导出文件。
3. 后续 stable 版本持续使用本项目签名，可通过应用内更新或下载本仓库新版 APK 升级。Android 会拒绝其他签名的 APK 覆盖本项目。

GitHub 的 stable 发布状态与内部 `stbeta` 构建名称无关；APK 文件名中的 beta 不表示它是 GitHub prerelease。签名公钥指纹与维护流程见 [发布说明](docs/release/README.md)。

## 使用 AI 字幕

播放带文本字幕的视频，长按字幕按钮进入字幕菜单，在 AI 区域配置服务、Key、目标语言及显示模式。AI 翻译需要自行配置兼容服务，可能产生服务商费用；原文播放和原文导出无需 Key。

完整操作、导出位置与排错方法见 [AI 字幕使用说明](ai-subtitle-user-guide.md)。字幕 ZIP 和脱敏诊断文件输出到 `Documents/SmartTube/Exports/`。

## 验证与限制

AI 功能此前经过两轮用户电视验收；每次发布另行运行单元测试、release lint、构建和 APK 签名验证。自动检查不等于所有电视环境验收。真实付费 API 调用、旧 Android 版本、存储权限拒绝和空间不足等边界验证状态以具体 release notes 为准。

问题请提交到 [本仓库 Issues](https://github.com/CometDash77/SmartTube-AI/issues)，注明版本与复现步骤，勿附 API Key。不要把本 fork 特有的 AI 问题直接提交给上游。
