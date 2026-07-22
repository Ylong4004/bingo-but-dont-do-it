# Bingo + DDI 项目交接上下文

> 更新日期：2026-07-19  
> 代码基线：`main` / `6d9d62a3980af89ad160f266c7979f2254b4e28b`  
> 目标环境：Minecraft 1.21.11、Java 21  
> 用途：给组员快速建立共同上下文，并明确当前语音识别的实际部署方式、已完成内容、测试状态与待验收事项。

## 1. 本次争议的结论

组员 Codex 的核心结论是正确的：当前实现是**逻辑服务端所在机器本地、离线执行语音识别**，不是每位玩家的客户端各自运行 Vosk，也不是把语音上传到第三方云端 ASR。

不过建议把原说法补充成下面这版：

> 玩家客户端由 Simple Voice Chat 采集麦克风并把 Opus 语音包发送到 Minecraft 服务端；服务端收到 `MicrophonePacketEvent` 后，才在服务端进程中完成 Opus 解码、16 kHz 重采样、Vosk 识别、关键词匹配和 DDI 扣血。推理过程不调用云端 ASR。专用服务器模式下，Vosk 模型只需要安装在服务器机器；单人或局域网房主使用集成服务器时，模型和识别负载位于房主的 Minecraft 进程。加入房间的其他客户端不需要模型。

逐项判断如下：

| 说法 | 判断 | 准确边界 |
| --- | --- | --- |
| “每位玩家在客户端本地跑识别” | 错误 | 客户端负责采集并通过 Simple Voice Chat 发送语音；本项目没有客户端 Vosk 识别入口。 |
| “服务端本地离线识别” | 正确 | Opus 解码、Vosk、命中判断和 DDI 权威结算都在逻辑服务端。 |
| “玩家不用下载模型” | 专用服务器下正确 | 模型只在服务端进程的工作目录下。集成服务器由房主机器承担模型和识别。 |
| “完全不需要网络” | 错误 | 不需要连接云端 ASR，但客户端到游戏服务器的 Simple Voice Chat 语音传输仍然需要网络。 |
| “网络延迟会降低识别质量” | 需要细分 | 稳定的固定延迟主要让触发变晚；丢包、明显抖动、断线或队列溢出才更容易破坏音节与分句。 |
| “模型下载好后全程不联网” | 对 ASR 推理正确 | 缺少模型时，启用语音玩法并开局会由服务端通过 HTTPS 下载一次官方模型，校验后本地加载；之后推理不请求云端。 |
| “语音完全不离开玩家电脑” | 错误 | 音频会送到当前 Minecraft/Simple Voice Chat 服务端，但不会再由本集成上传给第三方 ASR。 |

### 1.1 代码证据

- [`SimpleVoiceEntrypoint.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/SimpleVoiceEntrypoint.kt) 在 `VoicechatServerStartedEvent` 中取得 `VoicechatServerApi` 并创建识别后端；收到服务端 `MicrophonePacketEvent` 后把玩家 UUID 和 `opusEncodedData` 交给语音桥。
- [`VoiceKeywordBridge.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordBridge.kt) 只对当前确实持有语音词条的玩家放行音频，并把模型根目录定位到运行进程的 `user.dir/config/yet-another-minecraft-bingo/ddi/asr`。
- [`SimpleVoiceKeywordBackend.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/SimpleVoiceKeywordBackend.kt) 在服务端后台线程创建 Simple Voice Chat `OpusDecoder` 和 Vosk `Recognizer`，完成 48 kHz 到 16 kHz 的处理及最终结果匹配。
- [`DDIVoiceKeywordController.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDIVoiceKeywordController.kt) 把异步识别结果切回 Minecraft 服务端线程，再交给目标管理器重新校验和结算。
- [`DDIClientController.kt`](integration-ddi/src/client/kotlin/me/jfenn/bingo/client/integrations/ddi/DDIClientController.kt) 的职责是接收 DDI 状态包并更新 HUD；没有模型、解码器或识别器。

当前实际数据流：

```text
玩家客户端麦克风
  -> Simple Voice Chat 客户端编码并传输 Opus
  -> Minecraft / Simple Voice Chat 服务端 MicrophonePacketEvent
  -> 当前对局、授权、连接状态和当前语音词条的 O(1) 门控
  -> 服务端每玩家有界串行队列
  -> 服务端 Opus 解码、48 kHz -> 16 kHz
  -> 服务端本地 Vosk 有限语法识别
  -> 关键词/读音路径与置信度校验
  -> Minecraft 服务端主线程复核 gameId、objectiveId、revision、wordId
  -> DDI 扣血、换词、历史、淘汰和客户端 HUD 同步
```

`MicrophonePacketEvent` 没有被取消，所以普通近距离或分组语音仍会按 Simple Voice Chat 原流程广播；DDI 只旁路读取当前需要识别的音频包。

### 1.2 专用服务器与集成服务器

| 场景 | 模型、CPU、内存和下载流量所在机器 | 其他玩家是否需要 Vosk 模型 |
| --- | --- | --- |
| 专用服务器 | 专用服务器机器 | 不需要 |
| 单人游戏的集成服务器 | 本地玩家自己的 Minecraft 进程 | 没有其他玩家 |
| “对局域网开放”或房主开服 | 房主的 Minecraft 进程 | 加入者不需要 |

代码没有专服/集成服两套 ASR 分支，两者都由逻辑服务端的 `VoicechatServerStartedEvent` 驱动。当前游戏内提示写的是“仅在本机离线识别”，在专服语境下容易让人误解成“每位玩家电脑”；后续建议改成“仅在当前 Minecraft 服务端所在机器离线识别”。

因此，用户之前放在 `D:\PCL2\MinecraftPcl\versions\...\config\...\ddi\asr` 的模型，只有在该 PCL 实例本身承载单人/局域网集成服务器时才是正确位置；若玩家连接的是独立专用服务器，模型必须放在专服进程工作目录对应的 `config` 下。当前通用模组 JAR 物理上同时包含客户端会收到的 Vosk Java/native 代码，但非房主客户端不会创建 `Model`/`Recognizer`，也不会下载外部模型文件。

### 1.3 网络影响应怎样表述

- 固定且稳定的高延迟，主要表现为服务器晚收到音频、扣血和 HUD 更新晚发生，并不会单独改变已经收到的音频波形。
- 服务器连续约 700 ms 收不到同一玩家的新音频包时，当前实现会结束该分句。明显抖动可能把一个词拆成两段。
- 丢包会使关键音节缺失。本集成没有自行实现序号重排、抖动缓冲或额外 PLC，因此不能保证 Simple Voice Chat 上游已补偿所有丢包。
- 每位玩家的服务端队列上限为 400 个包。服务器过载造成队列溢出时会丢弃当前语句，这属于服务器性能问题，不应误诊为公网丢包。
- 服务端到客户端的 DDI 状态包延迟只影响 HUD 何时看到结果；扣血由服务端权威完成。

### 1.4 模型、下载和隐私边界

- 模型：`vosk-model-small-cn-0.22`。
- 默认目录：`<服务端进程工作目录>/config/yet-another-minecraft-bingo/ddi/asr/vosk-model-small-cn-0.22/`。
- 这里使用 `System.getProperty("user.dir")`，不是显式使用 Fabric Loader 的 `configDir`。专服若从不同工作目录启动，模型位置会随当前工作目录变化。
- 模型 ZIP 为 43,898,754 bytes，下载后校验固定 SHA-256、目录结构和解压上限，再原子安装。
- 语音功能默认关闭。管理员在大厅开启语音关键词并开局，等同于同意由服务器自动检查/下载模型；也可以用 `/bingo ddi voice model download` 手动准备。
- 当前只支持 x86-64 的 Windows、Linux 和 macOS；其他 CPU 架构会安全降级并排除语音词条。
- 本集成不落盘原始 Opus、PCM、完整识别文本或自定义关键词，也不把它们写入普通日志；诊断只保留计数、振幅、编码、置信度等非内容信息。
- 这不代表 Simple Voice Chat 音频不经过游戏服务器，也不代表能够识别代说、外放或录音回放；这些属于比赛规则边界。

## 2. 项目背景与共同目标

- `bingo` 来源于 horrificdev/bingo 的 `release/2.9.7` 标签。
- 原 `Dont_do_it` 来源于 `baiyueyue666/Dont_do_it`。
- DDI 服务端和客户端集成主体位于 [`integration-ddi`](integration-ddi/)。
- Simple Voice Chat、Opus 和 Vosk 的隔离层位于 [`integration-voicechat`](integration-voicechat/)。
- 目标不是在 Bingo 旁边简单并列一个小游戏，而是让 Bingo 得分、队伍协作、对抗、特殊事件和禁做词条共享同一局生命周期与胜负状态。

用户已经明确的长期要求：

- DDI 要能随 Bingo 真正启动、同步、计时、结束和恢复，`/bingo end` 必须同时结束 DDI。
- 支持按 Bingo 队伍进行“队伍共享”玩法：同队同词、共享生命，默认 3 心，归零整队淘汰；同时保留个人独立模式。
- 同队已经触发并扣过血的词条，本局不再为该队正常抽到；不同队的历史彼此独立。
- 敌队信息显示队名和队色，不显示过长的成员名；Tab 在玩家名后追加 `N♥`，不得占用或覆盖外部计分板。
- HUD 要像 Bingo 棋盘一样能在 Y 键设置中调整锚点、位置、缩放和背景透明度，视觉主题与 Bingo 接近。
- 大厅设置墙的“游戏特色 -> 不要做挑战”提供基础设置、特殊事件和语音关键词入口。
- 赛后面板要显示各队实际导致扣血的词条、触发者和扣血后的剩余生命。
- DDI 部分新增代码注释统一使用中文。
- 每次交付 JAR 命名为 `bingo-but-dont-do-it-1.21.11-v[版本号].jar`。

## 3. 最初审查问题与当前处理状态

| 原审查问题 | 当前状态 |
| --- | --- |
| 服务端 `DDIGameController` 只是惰性 Koin scoped 定义，未实例化 | 已接入 Bingo scope/plugin 的主动创建流程，控制器会监听对局生命周期。 |
| 客户端没有加载 DDI Koin 模块或创建客户端控制器 | 已从 1.21.11 客户端入口加载，DDI 网络包和 HUD 会注册。 |
| 开局不发送初始词条，`tickWordTimers` 没有调用点 | 已在权威目标初始化后同步；服务端游戏刻负责定时器和换词。 |
| Fabric 全局事件每局重复注册，回调随局数累积 | 进程级钩子只注册一次，对局状态通过当前 scope/session 路由和清理。 |
| 朝向区间重叠且方向状态离开后不复位 | 方位区间与进入/离开状态已修正。 |

上述基础修复及队伍共享核心主要落在提交 `018acf1`，后续提交继续完善 HUD、词条、特殊事件和语音。

## 4. 当前已经实现的 DDI 能力

### 4.1 生命周期、队伍与胜负

- DDI 只在 Bingo 对局进入 `PLAYING` 后运行。
- 默认配置为 DDI 关闭、个人独立模式、3 心和 60 秒换词；队伍共享、特殊事件和语音都需要在大厅显式开启。
- `/bingo end`、正常结束、回到大厅、scope 关闭或 DDI 提前决出结果都会停止 DDI，并清除词条、语音会话及特殊事件临时状态。
- `INDIVIDUAL`：每位玩家独立词条、计时和生命。
- `TEAM_SHARED`：按 Bingo 队伍生成一个 objective；同队成员共享词条、进度、计时、生命和淘汰状态。
- 队伍共享目标仍有在线成员时，单个队员掉线不会直接淘汰整队；目标失去全部有效成员时按规则弃权。
- Tab 通过玩家列表名称装饰器追加 `N♥`，没有创建、占用或修改计分板 objective。

### 4.2 HUD、敌队可见信息与赛后面板

- 玩家能看到自己/本队的生命和倒计时，但**看不到自己/本队当前禁做词条**；队伍共享时，词条会对该队所有成员隐藏，而不只是对某一个触发同步的玩家隐藏。
- 其他队伍区域显示队名、队伍颜色、生命和其当前禁做词条，不再拼接成员名字，避免文字挤压。旁观者可以查看所有队伍词条。
- DDI HUD 已进入 Y 键客户端配置，可调整位置、锚点、缩放和背景透明度。
- 赛后数据包携带每队的 DDI 扣血历史；结束面板按队显示词条、触发者和扣血后剩余心数。

### 4.3 词条与规则引擎

- 唯一内置词源是 [`words_v1.json`](integration-ddi/src/main/resources/data/yet-another-minecraft-bingo/ddi/words_v1.json)，不再在 Kotlin 与第二份词表间维护两套默认数据。
- 当前默认池共 356 条，ID 唯一；其中 40 条是内置语音关键词。
- 第一批已扩展常用合成、拾取、丢弃、放置、站立、破坏和持有类对象。
- 第二批已实现 29 条 Bingo 联动：任意格、截止前未完成格、中心、四角及 25 个固定坐标。
- 第三批已实现 12 条移动距离和 4 条敌队玩家有效伤害交互。
- 参数化规则支持命名空间 ID、标签/集合、次数或数量进度、依赖可用性、行为触发和截止失败。
- 队伍共享模式下规则进度由全队累计；传送、重生、换维度不会伪造移动距离。
- 正常随机抽取和无惩罚重抽都会遵守本队已触发历史；管理员 `word set` 是有意的调试覆盖，因此允许强制指定已触发词。

### 4.4 特殊事件

- 已适配原 DDI 的全部 30 种特殊事件，但重新实现了生命周期，未照搬原模组可能遗留状态的控制器。
- 设置墙支持总开关、间隔、平衡/资源/挑战/混乱预设，以及 30 项自定义事件池。
- 最近 3 次事件不重复。
- 临时实体、方块、BossBar、物品、玩家属性和状态在正常结束、强制停止、切换事件或 `/bingo end` 时走统一清理。
- 涉及生命变化的特殊事件按 DDI objective 结算；队伍人数不会让同一全局事件对共享生命重复计算。
- 完整事件清单和适配语义见 [`DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md`](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md)。

### 4.5 语音关键词

- 功能和玩家同意都默认关闭。
- 内置 40 条游戏常用语音词；每局可增加最多 32 条自定义关键词。
- 队伍共享模式只有在当前在线有效成员全部连接 Simple Voice Chat 且都明确同意时，语音词才进入该队候选池。
- 玩家授权命令：`/bingoprefs ddi_voice_consent true`。
- 后端、模型、native、连接或授权条件不满足时，语音词会从候选池排除；已分配但后来失效的语音词会无惩罚重抽，不影响其他 Bingo/DDI 内容。
- 每次词条变更都有 `assignmentRevision`；异步旧识别结果不能命中新词。

## 5. 最近语音识别问题、根因和修复

现场诊断曾出现以下情况：

- 麦克风包、目标包、入队、Opus 解码和 PCM 样本都在增加，但 Vosk 最终为空或只含未知词。
- “钻石”“敌人”“格子”等有时能识别；“帮我”“烈焰人”“苦力怕”“阴不阴”、部分语气词和局内自定义词难以识别。
- 有的短语必须说第二遍，略有噪音就更容易失败。

最终定位并修复了两个主要工程问题：

1. Windows 中文环境下 JNA 默认使用 GBK，而 Vosk 的 grammar 和 JSON C 接口要求 UTF-8。中文有限语法在原生边界损坏后会退化成 `[unk]`。现在加载模型和创建识别器前会强制初始化 UTF-8。
2. Vosk 中文小模型的动态 grammar 只接受其词典已有的词元，不能假设任意多字短语都是单一词元。现在所有内置语音词、别名和局内自定义关键词都走同一个通用生成器，加入完整词、逐汉字路径和不超过 7 个汉字的受限分词组合。每个口述短语最多生成 64 种候选，整份动态 grammar 最多 256 项，并用轮询分配避免别名较多的目标挤掉其他目标。

这里的“读音匹配”不是把 `yin bu yin` 这类拉丁拼音直接塞进中文 Vosk。中文汉字词元会映射到模型内部声学音节，再与声音比较，所以逐汉字 grammar 本身就是通用读音路径：

- 不为“阴不阴”或其他单个词写特例；内置词与局内自定义词使用相同路径。
- 同音字可能命中同一声学目标，这是按读音触发的预期代价。
- `yin` 与 `ying` 仍是不同音节，不应当无条件模糊成相同结果。
- “啊啊啊”“哈哈哈”这类纯重复语气词只有窄范围的重复次数容错；普通关键词仍要求完整音节序列，避免过度误触发。
- 没有启用无限制自由转写再做拼音模糊匹配，因为它会显著增加服务器 CPU 和误触发风险。

最新版已经使用**最终 v0.8 JAR、用户现有模型和离线 Windows 中文 TTS**做过端到端验证：

| 目标 | Vosk 最终结果 | 结果 |
| --- | --- | --- |
| 阴不阴 | `阴 不 阴` | 命中 |
| 帮我 | `帮 我` | 命中 |
| 烈焰人 | `烈焰 人` | 命中 |
| 运行时自定义“临时自定义关键词” | 逐字结果 | 命中 |

这证明编码、动态 grammar、分词、识别结果匹配和自定义词管线已经连通，但不等同于真实多人麦克风环境下识别率已经完全验收。

## 6. 设置入口和常用命令

大厅墙入口：

```text
游戏特色
  -> 不要做挑战
       -> 基础：启用、个人/队伍共享、初始生命、换词时间
       -> 特殊事件：启用、间隔、预设、自定义事件池
       -> 语音关键词：启用、后端状态、自定义关键词
```

大厅设置命令：

```text
/bingo options ddi
/bingo options ddi enable [true|false]
/bingo options ddi mode individual
/bingo options ddi mode team
/bingo options ddi hearts <1..20>
/bingo options ddi timer <10..600>

/bingo options ddi events enable [true|false]
/bingo options ddi events interval <秒>
/bingo options ddi events preset <balanced|resource|challenge|chaos>
/bingo options ddi events include <event_id>
/bingo options ddi events exclude <event_id>
/bingo options ddi events list

/bingo options ddi voice enable [true|false]
/bingo options ddi voice keyword add <关键词>
/bingo options ddi voice keyword remove <关键词>
/bingo options ddi voice keyword list
/bingo options ddi voice keyword reset
/bingoprefs ddi_voice_consent <true|false>
```

局内管理、测试和诊断命令：

```text
/bingo ddi status [reveal]
/bingo ddi sync [player]

/bingo ddi event status
/bingo ddi event trigger <event_id>
/bingo ddi event stop

/bingo ddi word next <word_id>
/bingo ddi word next status
/bingo ddi word next clear
/bingo ddi word set <player> <word_id>
/bingo ddi word reroll <player>
/bingo ddi word reroll all
/bingo ddi word info <word_id>

/bingo ddi voice status
/bingo ddi voice model download
/bingo ddi voice debug
/bingo ddi voice debug <player>
/bingo ddi voice debug reset
/bingo ddi voice simulate <player>
```

调试命令默认要求 OP/对应 DDI 管理权限。实际命令补全和当前源码是最终准则；完整语音流水线判断表见 [`DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md`](DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md)。

推荐语音实机排查顺序：

1. 大厅开启 DDI 和语音关键词，玩家执行 `/bingoprefs ddi_voice_consent true`。
2. 用 `/bingo ddi word next voice_diamond` 固定下一局首词，或局内用 `word set`。
3. 执行 `/bingo ddi voice debug reset`。
4. 查询 `/bingo ddi voice debug <player>`，确认参赛、有效、授权、语音连接、语音词条和目标发布都为 `true`。
5. 单人完整说一次目标词，松开 PTT 或停顿至少约 0.7 秒，再查询诊断。
6. 若想排除 ASR 之后的结算问题，执行 `/bingo ddi voice simulate <player>`；它能扣血表示问题位于音频、Opus、Vosk 或匹配阶段。

## 7. 当前构建、依赖与自动化验收

当前本地成品：

```text
mc1.21.11/build/libs/bingo-but-dont-do-it-1.21.11-v0.8.jar
```

- 大小：59,339,955 bytes。
- SHA-256：`151574061E46A0E96A22F8AC08B03DE43EDD9A2E7D9A9AA340853B1FF786617F`。
- 该 JAR 是本地构建产物，默认不应视为已经提交到 Git；组员拉取源码后可自行构建，或单独接收成品。
- 当前测试报告：104 tests，0 failures，0 errors，0 skipped。
- 完整 `build` 已成功。

运行要求：

- Minecraft 1.21.11。
- Java 21 或更高。
- Fabric Loader 元数据最低 `0.16.9`，当前测试/推荐 `0.18.4`。
- Fabric API 元数据最低 `0.140.0+1.21.11`，当前测试/推荐 `0.140.2+1.21.11`。
- Fabric Language Kotlin `1.13.7+kotlin.2.2.21` 已作为 nested JAR 包含在当前成品中。
- 普通 Bingo/DDI 不强制要求 Simple Voice Chat；要启用语音关键词，服务器和参与客户端都需要 Minecraft 1.21.11 对应、版本不低于 `2.6.20` 的 Simple Voice Chat Fabric 版。
- YACL 是可选客户端依赖；不安装不影响对局，但无法使用完整的 Y 键 HUD 高级配置界面。

注意：标准 Gradle 构建仍生成 `bingo-2.9.7+mc1.21.11.jar`；当前 `v0.8` 文件是构建后按交付约定复制/重命名的，命名规则尚未写入 Gradle。其 `fabric.mod.json` 内部模组版本也仍继承上游，为 `2.9.7+mc1.21.11`。不要把交付文件名版本和 Fabric 元数据版本混为一谈。

## 8. Git 里程碑

| 提交 | 内容 |
| --- | --- |
| `018acf1` | 修复初始 DDI 生命周期问题，完成核心集成和队伍共享模式。 |
| `a3c7145` | 在大厅“游戏特色”中加入独立 DDI 设置页面。 |
| `a843e2c` | 完善可移动 HUD、Tab 生命后缀和队伍词条去重。 |
| `e877ca7` | 参数化词条、Bingo 棋盘联动、移动和玩家交互批次。 |
| `276c867` | 30 种特殊事件、语音关键词、设置菜单及局内调试。 |
| `6d9d62a` | 修复 Windows 中文 Vosk UTF-8、通用词元/读音路径和诊断。 |

## 9. 已知边界与下一轮优先验收

以下不是“功能完全没做”，而是自动化和单机端到端测试无法替代的真实多人验收：

1. 至少 2 个客户端、连续 3 局，验证全局事件和语音回调不会累积，`/bingo end` 后所有状态都能清理。
2. 在专用服务器上分别测试固定延迟、抖动、丢包和服务器 CPU 压力，不能只用“网络不好”概括。
3. 8 人和更大规模下观察 ASR 工作线程、每玩家队列、内存和服务器 TPS；识别负载由服务器承担。
4. 用真实 PTT、不同麦克风、口音、背景噪音测试内置 40 词和局内自定义词，统计漏报和误报。
5. 验证拒绝语音授权、掉线重连、队伍成员部分未连接语音时，语音词会被正确排除或无惩罚重抽。
6. 验证外部模组对玩家列表名称、HUD 和 Mixin 的兼容性。

其他当前边界：

- DDI 运行态没有跨服务器重启持久化；`/bingo resume` 会在旧 DDI 状态已清理后，为同一 Bingo 对局重新建立新的生命、词条和计时器。
- 赛后扣血历史目前没有独立条数上限，极长对局可能增大保存快照和赛后网络包。
- 当前交付与自动化验收只针对 Minecraft 1.21.11。
- Bingo 连线、领先/落后、格子难度/类别、连续完成多格等高级联动，以及指定敌方玩家、队友物品交换和更多实体交互，仍是后续词条计划，不要误写成已经完成。
- Whisper 或其他自由转写后端仍只是未来可选方案，当前生产实现只有 Vosk 有限语法后端。

建议的两个小型代码清理项：

- 把玩家提示“仅在本机离线识别”改为“仅在当前 Minecraft 服务端所在机器离线识别”。
- [`VoiceKeywordBridge.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordBridge.kt) 中“除此之外不会发起网络请求”的旧注释与当前“开局自动下载模型”行为不完全一致，应同步修正文案。

不要把同音容错理解成无限模糊匹配。若把所有拼音近似音、自由转写和短片段都放宽，确实可能提高部分口音的召回率，但会显著提高误扣血概率和服务器 CPU；任何进一步放宽都应作为可选实验后端，先做对照数据。

## 10. 关键路径与文档有效性

关键源码：

- DDI 权威状态与结算：[`DDIObjectiveManager.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDIObjectiveManager.kt)
- 对局生命周期：[`DDIGameController.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDIGameController.kt)
- 触发检测：[`DDITriggerDetector.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDITriggerDetector.kt)
- 词池：[`DDIWordPool.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDIWordPool.kt) 与 [`words_v1.json`](integration-ddi/src/main/resources/data/yet-another-minecraft-bingo/ddi/words_v1.json)
- 大厅页面：[`DDIMenu.kt`](common/src/main/java/me/jfenn/bingo/common/menu/DDIMenu.kt)
- HUD：[`DDIHudRenderer.kt`](integration-ddi/src/client/kotlin/me/jfenn/bingo/client/integrations/ddi/DDIHudRenderer.kt)
- Tab 生命：[`DDITabLivesService.kt`](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDITabLivesService.kt)
- 语音服务端入口：[`SimpleVoiceEntrypoint.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/SimpleVoiceEntrypoint.kt)
- 语音模型：[`VoiceKeywordModelManager.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt)
- 语音词元与结果匹配：[`VoiceKeywordText.kt`](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordText.kt)

现有 Markdown 是分阶段记录，部分旧章节保留了当时方案或旧版本结果。例如早期审计文档曾讨论用计分板显示 Tab 生命，但当前代码已经改成玩家名装饰器，不会占用外部计分板。发生冲突时请按以下优先级判断：

```text
当前提交源码与测试
  > 本交接文档中针对 6d9d62a 的结论
  > 各阶段实施记录
  > 早期计划/审计中的候选方案
```

相关阶段文档：

- [`DDI_INTEGRATION_AUDIT_AND_PLAN.md`](DDI_INTEGRATION_AUDIT_AND_PLAN.md)：初始审查、架构和逐阶段记录，含部分历史方案。
- [`DDI_WORD_EXPANSION_PLAN.md`](DDI_WORD_EXPANSION_PLAN.md)：四批词条的范围、实施结果和后续候选。
- [`DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md`](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md)：30 种事件和 v0.5 语音设计。
- [`DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md`](DDI_DEBUG_AND_VOICE_DIAGNOSTICS.md)：最新中文语音根因、词元路径和诊断命令。

## 11. 给接手组员的最短摘要

1. 拉取并以 `6d9d62a` 为共同基线。
2. 当前 ASR 是服务端本地离线，不是每客户端识别；客户端仍通过 Simple Voice Chat 把 Opus 发到服务器。
3. 专服只在服务器装模型；房主集成服则由房主机器承担模型和算力。
4. 稳定延迟主要增加等待，丢包、抖动、分句和服务器过载才更直接影响识别率。
5. DDI 生命周期、队伍共享、HUD/Tab、赛后历史、356 条词、30 个特殊事件、语音词和调试工具均已落地。
6. v0.8 已通过 104 项自动化测试和本地端到端中文语音测试，但真实多人连续多局与弱网/负载测试仍需共同完成。
