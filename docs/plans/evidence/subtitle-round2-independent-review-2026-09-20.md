# 第二轮验收报告独立复核

日期：2026-09-20，Asia/Hong_Kong。源码基线：`production` / `3e6a0d24`。
范围：原始诊断、报告结论、相关来源/菜单/导出调用链及后续方案；不是全库审核。
本轮只交付文档，用户自行转交另一个 agent 实施；未运行应用、设备实验或新 CI。

## 结论

**维持第二轮验收不通过；接受产品缺陷方向，但原报告不能原样作为已证实根因。**
导出失败明确落在“没有可导出的时间轴”分支；三个诊断时刻来源未绑定，未见实际抓取启动事件。
没有证据确定用户没选轨，也不能排除来源绑定或回调时序缺陷，更不能据此证明 N1 无回归。

用户在本次复核中明确补充：**“我确实没有地方输入也没有输入过key”**。
“从未输入 Key”现在有用户直接证据，不再需要从 `keyConfigured=false` 推断。
“没有地方输入”须作为未解决的可用性问题：入口发现、焦点进入、键盘/输入、保存反馈均需验收。
源码存在 `showPassword` 调用不能推翻用户体验；仅改标签不能证明输入链可用。

更新后的执行顺序和验收条件见 [实施计划](../subtitle-round3-implementation-plan.md)；主计划 §21 为当前入口。

## 1. 原始日志重新计数

以下是本轮读取原始文件后重新计算的结果；计数只包括**报告内已有事件**，不把生成这份报告本身的成功事件补进去。

| 日志时间 | eventCount | TIMELINE_REQUESTED | 字幕 NO_TIMELINE | EXPORT_DIAGNOSTICS_OK |
| --- | --- | --- | --- | --- |
| 11:30:03 / nightly-9 | 15 | 3 | 0 | 0 |
| 12:47:40 / nightly-18 | 4 | 0 | 3 | 0 |
| 12:48:54 / nightly-18 | 14 | 0 | 11 | 1 |
| 12:49:08 / nightly-18 | 17 | 0 | 12 | 2 |

后三份是同一事件序列的前缀快照，按时间偏移和事件码去重为 **12 次字幕失败、2 次诊断成功**。
原报告的 `3/13/13`、`0/2/2`、失败总数 29 均不准确；既不能相加，也不能把输出文件存在等同于内部事件计数。
最新序列：AI 开启前 8 次失败，开启后 3 次，关闭后 1 次。

原始文件位置：`device-logs/2026-09-20/SmartTube-diagnostics-20260920-*.txt`。
本轮重算 SHA-256 与仓库报告所列前缀一致（没有原附件可再作独立比对）：

| 时间 | SHA-256 |
| --- | --- |
| 11:30:03 | `5ace2bbc40d388590b178b69081cd7b039401974f878e20d2591784220416a44` |
| 12:47:40 | `a82d1d8f52519f11c56e4f0b63ba725a93f39ce8262556b2205fed5aebf4a9bc` |
| 12:48:54 | `95b5e82e0bf89293e80b2056165d628003147dc10e0980f974b58748ea375b74` |
| 12:49:08 | `b5758ee9d554aa759360e538dc9bc5fd0684d8a29a3dadf47222d0c0aa26fe03` |

## 2. 逐项裁决与反例

下列 Java 路径以 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/` 为根，行号对应基线。

| 结论 | 裁决及影响 | 源码/证据 |
| --- | --- | --- |
| 本次失败是写文件失败 | 否。`NO_TIMELINE` 在构建 bundle 后、进入 writer 前返回；两次诊断成功只证明对应诊断写入成功，不证明所有 ZIP/权限/空间路径正常 | `exoplayer/other/SubtitleExportController.java:214–233` |
| AI 开关决定原文导出 | 否。该导出分支不要求 AI/Key；日志开关前后都失败。不能将此扩张为所有 AI 生命周期路径无缺陷 | 同上；`SubtitleTimelineCoordinator.java:121–165` |
| 没选字幕轨已经证实 | 否。`sourceBound=false` 也可能是已选但 unknown/ambiguous/unbound；这些日志未记录选轨状态 | `SubtitleSourceBinder.java:106–143` |
| 0 个请求事件代表请求入口没执行过 | 否。入口和协调器有多个静默返回；可说“保留的会话事件中没有实际启动记录”，不能说“没有执行过入口/没有任何网络请求” | `app/presenters/PlaybackPresenter.java:453–462`；协调器 `121–165` |
| Key 从未输入/保存 | 用户本轮确认从未输入。日志单独只能证明诊断时 `isKeyConfigured()` 为 false，不含历史保存事实 | Presenter `798–813`；用户新指示 |
| 入口、保存反馈和 AI_OFF 文案有缺陷 | 接受。Key 入口与 hint 共用“AI 翻译设置”；持久保存无成功提示；AI_OFF 落入 translating。实际遥控器无法输入的机制未重现 | `PlayerUIController.java:412–435,597–611`；`utils/SimpleEditDialog.java:31–51` |
| N1 新旧守卫“同源同真” | 不接受该证明。当前 source 从 host 解析，sourceKey 从会话 controller 读取；存在两个状态读取点，等价需要回调同步不变量 | Presenter `585–607`；`SubtitleTimelineScheduler.java:80–86`；N1 前 Presenter `436–453` |
| N1 导致失败 | 未证实。保留调查项，不回退 N1，不把等价证明不成立当作回归已成立 | 新旧代码与诊断均不足以定位本次时序 |
| 不同证书导致本轮偏好清空 | 两个旧候选证书差异是历史核验记录，本轮未重验 APK；不等于已证明 nightly-18 安装经过及 CC 重置。默认值不能证明卸载历史 | 原交付记录；本轮无安装/备份/升级记录 |
| 新日志一定能直接指出最终根因 | 过强。它应定位未选轨、绑定失败、守卫或重用等边界；绑定为什么失败可能仍需可控重现 | 下述观测计划 |

## 3. 原修复方案遗漏的正确性问题

1. **选中不等于绑定。** `PlayerUIController.java:330–334` 把 `getSubtitleFormat()!=null` 传给语义为 `sourceBound` 的参数。`ExoFormatItem.java:64–80` 甚至可包装 format 为空的 MediaTrack；不能将这个包装对象作为有效文本轨证据。菜单、诊断应使用同一次读取的真实格式/绑定结果，分别保留 selected 与 bound。
2. **getStatus 不刷新状态。** `ExoPlayerController.java:291–299,311–312` 中 resolve 在 `getSelectedSubtitleSource()`，单独 `getStatus()` 只读上次结果。新增 host accessor 必须以当前轨刷新后再读取；无 host/无 display 与 NOT_SELECTED 要区分，避免无播放器时指责用户没选字幕。
3. **失败解释必须属于点击时快照。** 导出已在 UI 线程捕获 snapshot，但 `PlayerUIController.java:590–594` 在回调时读取 presenter 最新状态。新增失败原因应由 snapshot 计算并随 `ExportResult` 返回，不能只给 snapshot 加字段却继续读实时状态。
4. **已绑定不代表“等几秒”总会恢复。** 解码不支持、IO_FAILED、payload 限制等终态需指向换轨/重试/诊断；只有明确在飞时才能说正在准备。等待不应伪装成自动重试承诺。
5. **日志格式过滤不是语义隐私证明。** 大写字母数字也能组成秘密。只记录固定枚举/布尔/计数，禁止把 URL、异常消息、sourceKey、locator 或输入文本拼接成事件；保持 40 条有界环。

## 4. Jev 辅助审核及人工裁决

实际 live `audit` **1 次 / 6 项**；模型 `typesafe/jev-1.13-20260917`，输入 6316 tokens，输出 258 tokens，耗时约 1099ms。
没有重试、没有服务失败回退；3 项需人工复核阈值。不据此宣称效率提升、测试通过或任务完成。
精确输入及原 stdout：[input](subtitle-round2-review-jev-input.json)、[result](subtitle-round2-review-jev-result.json)。
仅向 Jev 发送相关公开源码片段，没有发送设备日志、字幕正文、配置或凭据。

| ID | Jev choice / probability / confidence | Astra 最终裁决 |
| --- | --- | --- |
| selected-is-bound | supports / .60 / .40（未达阈值） | **contradicts**；调用方传格式包装对象是否非空，不是绑定结果；补查 ExoFormatItem 确认反例 |
| export-writer | supports / .91 / .87 | supports；NO_TIMELINE 先于 writer 返回 |
| off-label | supports / .98 / .97 | supports；AI_OFF 确实未显式映射 |
| binding-status | contradicts / .89 / .84 | contradicts；getStatus 仅返回字段 |
| request-event | contradicts / .45 / .17（未达阈值） | contradicts；入口被调用但 guard 返回就无事件 |
| key-history | insufficient / .74 / .62（未达阈值） | 对所给源码仍 insufficient；用户随后新增直接确认“未输入”，独立记录，不篡改既有 Jev 输入/结果 |

## 5. Ponytail 范围裁剪与开放问题决议

- 原报告 §5: shrink: 不引入通用诊断框架；扩展既有快照、协调器结果和固定白名单即可。
- 原报告 §6 P2: shrink: 无轨即时提示与 P0-1 使用同一原因映射；不重复实现预检查状态机。
- 原报告 §8: yagni: 不从屏幕 cue 拼造完整原文、不加后台自动找轨/轮询抓取、不重建设置中心。保留已有导出契约和字幕菜单，先让 Key 入口可发现且可输入。
- 原报告 §8: shrink: 签名作为项目签名 RC/覆盖升级的出口前置；不阻塞修复、fake 输入验证或无 Key 原文验证。不同签名不默认卸载。
- 原报告 §8: shrink: 选轨触发缺口先用回调重现测试定位；有失败证据再补真实字幕选轨触发，刷新 source 身份后走原协调器，不碰视频/音频选轨或加重复下载路径。

net: 尚无实施 diff，不能诚实量化可删行数；本轮删除的是重复/推测性工作范围。

## 6. 验证边界

本轮核验了本地源码、历史 N1 前守卫、日志字段/事件/哈希、Git 变更范围及 CI 配置。
`666f5177..3e6a0d24` 无应用 Java 修改，但含 `.github/workflows/CI.yml` 的一行改动，因此“未改应用代码”成立，“完全没有配置改动”不成立。
共享模块仍使用仓库内 checkout（两个 sibling 路径不存在），未修改子模块。
CI 现配置为单测 JDK 11、lint/build JDK 17；本轮未查远端运行和 Secrets 新状态，沿用历史报告时均应标明历史。
文档/JSON 检查不替代 Java 编译、单测、APK、签名及设备验收。
