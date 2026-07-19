# Bingo × Don't Do It

<p align="center">
  <img src="docs/assets/bingo-but-dont-do-it-logo.png" alt="BINGO But Don't Do It! 模组文字标志" width="720">
</p>

> 一个基于 **[Yet Another Minecraft BINGO](https://gitlab.com/horrificdev/bingo/-/tree/release/2.9.7?ref_type=tags)** 的非官方魔改整合版：把 **[Don't Do It](https://github.com/baiyueyue666/Dont_do_it)** 的“不要做挑战”玩法移植、重构并融入 Bingo 对局。

[Minecraft 1.21.11](#运行环境) · [Fabric Loader 0.16.9+](https://fabricmc.net/) · [LGPL-3.0 + MIT](LICENSE)

本仓库是 **Bingo 和 Don't Do It 的魔改缝合项目**，不是上述任一上游项目的官方续作、官方分支或官方发布版本。

- Bingo 上游：[`horrificdev/bingo` · release/2.9.7](https://gitlab.com/horrificdev/bingo/-/tree/release/2.9.7?ref_type=tags)
- Don't Do It 上游：[`baiyueyue666/Dont_do_it`](https://github.com/baiyueyue666/Dont_do_it)
- 本项目仓库：[`Ylong4004/bingo-but-dont-do-it`](https://github.com/Ylong4004/bingo-but-dont-do-it)

如果你只想了解原版 Bingo 的棋盘、目标、队伍和大厅功能，请阅读 [Bingo 官方 Wiki](https://horrific.dev/bingo/)；本文重点介绍本项目新增的 DDI 玩法。

## 不要做挑战（DDI）是什么？

DDI 会在 Bingo 对局中为每名玩家或每支 Bingo 队伍持续抽取一条“**不要做**”词条。触发当前词条会扣除生命并立刻换词；词条倒计时结束时只换词、不扣血。生命归零即被 DDI 淘汰。

推荐使用“**队伍共享**”模式：同一 Bingo 队的成员共用一条词条和一份生命池（默认 `3♥`）。队友任意一人触发，整队只扣一次心；敌方队伍能看到该队的词条以便针对，本队无法看到自己的词条，避免通过 HUD 反推答案。

DDI 不会取代 Bingo 的主胜利条件：正常完成 Bingo 胜利条件仍可结束对局；`/bingo end` 也会一并停止 DDI。结算页会额外列出每支队伍因哪些词条被扣血。

### 已整合内容

- **队伍与生命**：个人独立、Bingo 队伍共享两种模式；队伍生命归零后淘汰并转为旁观。
- **对抗信息**：敌方词条、剩余生命和倒计时显示在 DDI HUD；Tab 列表在玩家名称后附加 `N♥`，不占用或覆盖外部计分板。
- **词条池**：内置 356 条词条，涵盖基础动作、物品/方块、合成、拾取、丢弃、站立环境、移动距离、Bingo 棋盘、玩家交互与语音关键词。
- **防重复**：一支队伍（或个人）本局已经触发过的词条不会再次被随机抽中。
- **特殊事件**：移植并适配 Don't Do It 的特殊事件，可在大厅选择预设或自定义事件池；事件会随对局结束统一清理。
- **可配置 HUD**：DDI HUD 可通过与 Bingo 棋盘相同的客户端设置调整位置；安装 YACL 后可使用完整的 Y 键设置界面。
- **大厅设置墙**：在“游戏特色”页面中可开关 DDI、特殊事件与语音关键词，并调整常用选项。
- **管理员调试**：可指定下一局词条、局内强制换词、检查语音识别流水线和手动触发/停止特殊事件。

## 快速开始

1. 服务端与所有希望看到 DDI HUD 的客户端均安装同一份本模组 JAR、Fabric API 和 Fabric Language Kotlin。
2. 进入 Bingo 大厅，在设置墙的“游戏特色”中启用“不要做”；推荐将模式设为“队伍共享”、生命设为 `3`。
3. 按正常流程分配 Bingo 队伍并开始游戏。至少需要两支有效队伍，DDI 才会启动。
4. 玩家可按 `Y` 打开客户端设置，调整 Bingo 棋盘与 DDI HUD 的位置。未安装客户端模组的玩家仍可加入服务器，但无法获得 DDI HUD 和客户端便利功能。

## 运行环境

| 项目 | 要求 | 建议版本 |
| --- | --- | --- |
| Minecraft | `1.21.11` | `1.21.11` |
| Java | `21` | Java 21 |
| Fabric Loader | `>= 0.16.9` | `0.18.4` |
| Fabric API | `>= 0.140.0+1.21.11` | `0.140.2+1.21.11` |
| Fabric Language Kotlin | 必需依赖 | `1.13.7+kotlin.2.2.21` 或兼容版本 |
| YetAnotherConfigLib（YACL） | 可选；提供完整 HUD 设置页 | 与 1.21.11 兼容版本 |

发布的文件名遵循：`bingo-but-dont-do-it-1.21.11-v<版本号>.jar`。

## 语音关键词（实验功能）

语音关键词默认关闭。启用后，抽到语音词条的玩家说出目标关键词，会触发对应 DDI 词条并扣除队伍生命。该功能依赖 **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**：

- 服务器及参与语音的客户端需使用 `1.21.11-2.6.20` 或更高兼容版本。
- 玩家必须自行执行 `/bingoprefs ddi_voice_consent true` 同意处理，未同意者不会被抽到语音词条；队伍共享模式要求该队在线有效成员全部同意。
- 识别在**当前 Minecraft 服务端所在机器**本地离线运行，服务端从 Simple Voice Chat 接收语音包后解码并交给 Vosk；不会把音频或转写上传到云端。
- 语音包从客户端到游戏服务器仍需经过 Simple Voice Chat 网络，丢包、高延迟、环境噪声、口音和小模型能力都会影响结果。
- 首次使用时以管理员身份执行 `/bingo ddi voice model download` 下载 `vosk-model-small-cn-0.22`；模型安装在服务端 `config/yet-another-minecraft-bingo/ddi/asr/`。之后离线加载即可，玩家无需各自下载模型。
- 内置关键词和局内自定义关键词都会走汉字词元与读音路径匹配；它不是通用语音转写服务，短语越清晰、停顿越完整，识别越稳定。

语音不会写入普通日志或落盘保存。请在开局前告知所有参与者已启用该实验玩法；播放录音、代说和外放无法被可靠地区分。

## 常用命令

以下 DDI 管理命令默认需要 OP/管理员权限。常用开关也可直接在大厅设置墙操作。

| 命令 | 用途 |
| --- | --- |
| `/bingo options ddi enable <true\|false>` | 开关 DDI。 |
| `/bingo options ddi mode team` | 使用 Bingo 队伍共享词条与生命。 |
| `/bingo options ddi mode individual` | 使用个人独立词条与生命。 |
| `/bingo options ddi hearts <数量>` | 设置个人或队伍的最大生命。 |
| `/bingo options ddi timer <秒>` | 设置词条倒计时（`10`–`600` 秒）。 |
| `/bingo options ddi events enable <true\|false>` | 开关特殊事件。 |
| `/bingo options ddi events interval <秒>` | 设置特殊事件出现间隔。 |
| `/bingo options ddi events preset <预设>` | 选用特殊事件预设池。 |
| `/bingo options ddi events include <事件ID>` | 将事件加入自定义事件池。 |
| `/bingo options ddi events exclude <事件ID>` | 从自定义事件池移除事件。 |
| `/bingo options ddi events list` | 列出可选特殊事件。 |
| `/bingo options ddi voice enable <true\|false>` | 开关语音关键词候选词条。 |
| `/bingo options ddi voice keyword add <关键词>` | 添加本局自定义语音关键词（最多 32 条）。 |
| `/bingo options ddi voice keyword remove <关键词>` | 删除一条自定义关键词。 |
| `/bingo options ddi voice keyword list` | 列出自定义关键词。 |
| `/bingo options ddi voice keyword reset` | 清空本局自定义关键词。 |
| `/bingoprefs ddi_voice_consent <true\|false>` | 玩家设置自己的语音处理同意状态。 |
| `/bingo ddi status [reveal]` | 查看 DDI 对局状态；`reveal` 显示完整词条。 |
| `/bingo ddi event status` | 查看特殊事件状态。 |
| `/bingo ddi event trigger <事件ID>` | 手动触发一个特殊事件。 |
| `/bingo ddi event stop` | 停止当前特殊事件并清理其临时效果。 |
| `/bingo ddi word next <词条ID>` | 指定下一局所有 DDI 目标的首条词条。 |
| `/bingo ddi word set <玩家> <词条ID>` | 局内为该玩家所属目标强制发放词条。 |
| `/bingo ddi word reroll <玩家\|all>` | 局内无惩罚换词。 |
| `/bingo ddi word info <词条ID>` | 查看词条的显示文本、类别与触发规则。 |
| `/bingo ddi voice status` | 查看语音后端与模型状态。 |
| `/bingo ddi voice model download` | 在服务端下载中文 Vosk 模型。 |
| `/bingo ddi voice debug [玩家\|reset]` | 查看或重置语音识别诊断计数。 |
| `/bingo ddi voice simulate <玩家>` | 跳过音频识别，直接验证 DDI 语音结算链路。 |

完整的调试与排障说明见 [DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md](DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md)。词条扩展计划见 [DDI_WORD_EXPANSION_PLAN.md](DDI_WORD_EXPANSION_PLAN.md)。

## 词条与玩法说明

词条定义集中在 [`integration-ddi/src/main/resources/data/yet-another-minecraft-bingo/ddi/words_v1.json`](integration-ddi/src/main/resources/data/yet-another-minecraft-bingo/ddi/words_v1.json)。若要改动词条池，请保留唯一的稳定 `id`，并在实际对局中验证对应的触发器。

DDI 的设计目标是让队伍在推进 Bingo 棋盘的同时观察敌方词条、制造压力并规避风险，而不是单独玩一局 Bingo 或单独玩一局惩罚游戏。建议先使用中等词条时长、3 点共享生命、两到四支队伍进行测试，再按你们的对局节奏调节词条权重、特殊事件和语音开关。

## 已知限制

- 当前只验证并发布 Minecraft `1.21.11`；其他 Minecraft 版本尚未完成 DDI 适配验证。
- DDI 对局状态暂不跨服务端重启持久化；`/bingo resume` 会创建一段新的 DDI 会话并重新分配词条与生命。
- 语音关键词是可选实验功能，面对多人同时说话、噪声、弱网络或非常规发音时可能漏检或误检。请先用调试命令完成本地联机测试。
- DDI 扣血历史目前不设条数上限；极长对局可能增大结算数据量。

## 构建

在仓库根目录、使用 Java 21 执行：

```powershell
.\gradlew.bat :mc1.21.11:build
```

构建产物先生成于 `mc1.21.11/build/libs/`。发布前请将要分发的 JAR 复制/命名为 `bingo-but-dont-do-it-1.21.11-v<版本号>.jar`，并以实际测试结果替换版本号。

## 鸣谢与来源

- Bingo 的主体框架、Bingo 游戏、大厅、HUD 体系及大量原有功能来自 [Yet Another Minecraft BINGO](https://gitlab.com/horrificdev/bingo/)，作者与贡献者名单请见其上游仓库。
- 不要做挑战的原始玩法思路、词条与特殊事件来源于 [baiyueyue666/Dont_do_it](https://github.com/baiyueyue666/Dont_do_it)，作者 Baiyueyue。
- 本项目在两者基础上完成了 1.21.11 适配、生命周期与网络同步重构、Bingo 队伍共享机制、HUD/大厅设置整合、结算页、调试工具与可选离线语音关键词等改造。
- 感谢 Fabric、Fabric API、Fabric Language Kotlin、Simple Voice Chat、YACL 与 Vosk 的作者和社区。

## 许可证

本仓库是一个包含不同上游授权内容的组合修改项目：

- Bingo 上游及基于其修改的主体代码按 **GNU Lesser General Public License v3.0（LGPL-3.0）** 提供。
- 从 Don't Do It 移植或改编的部分同时保留其 **MIT License** 版权与许可声明，版权所有者为 Baiyueyue。
- 具体完整文本、来源说明与再分发要求请阅读 [LICENSE](LICENSE)。发布二进制或再分发源代码时，请保留上述授权与署名信息。

Minecraft 是 Mojang Studios 的商标；本项目与 Mojang Studios、Microsoft、Bingo 上游或 Don't Do It 上游均无官方隶属关系。
