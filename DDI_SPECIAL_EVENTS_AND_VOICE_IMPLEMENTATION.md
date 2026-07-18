# DDI 特殊事件与语音关键词实施记录

> 方案日期：2026-07-17
> 目标版本：v0.5（Minecraft 1.21.11）
> 当前状态：v0.5 已完成并通过自动化测试与正式构建

## 1. 范围与设计结论

本次包含两项互相独立、默认关闭的实验玩法：

1. 将原 `Dont_do_it` 的 30 种全局特殊事件适配到 Bingo DDI；
2. 完成 DDI 语音关键词词条，支持内置游戏常用词与局内添加自定义关键词。

两项功能都服从 Bingo 的权威对局生命周期：只在 DDI 已启用且游戏处于
`PLAYING` 时运行，`/bingo end`、回到大厅、关闭 scope 或 DDI 提前决出胜负时
都必须停止，并先清理临时世界状态。

原特殊事件控制器不会在游戏被命令结束、局中关闭事件或强制切换事件时调用
清理，可能永久残留南瓜头、幼体属性、铁笼、粘液块、临时矿石和商人。本次只
复用事件内容，不复用该生命周期实现。

## 2. 设置墙结构

DDI 主页面保留基础规则，并增加两个二级入口：

```text
游戏特色
  -> 不要做挑战
       -> 基础：总开关 / 个人或队伍共享 / 初始生命 / 换词时间
       -> 特殊事件：开关 / 间隔 / 预设 / 自定义事件池
       -> 语音关键词：开关 / 后端说明 / 自定义关键词
```

特殊事件页面提供：

- 默认关闭的总开关；
- `60 / 180 / 300 / 420` 秒快捷间隔和数值调整；
- “平衡、资源、挑战、混乱”预设；
- 30 个事件的分页多选，自定义池至少保留一个事件；
- 管理员运行态测试命令使用稳定英文 ID，不使用本地化显示名。

“平衡”是默认事件池，但只有打开总开关后才运行。它排除会永久改变 Bingo
资源进程、造成大量实体或有明显破坏性的事件；被排除的事件仍可在自定义池中
手动启用。

语音页面提供：

- 默认关闭的“语音关键词”总开关；
- Simple Voice Chat、本地 Vosk 与模型要求说明；
- 当前自定义关键词数量；
- 1.21.11 文本输入 Dialog，用于添加关键词；
- 命令形式的列出、移除与重置入口，保证游戏进行中也能调整。

## 3. 特殊事件目录与适配策略

共 30 种事件；除“交易商人”外继承原权重。随机器排除最近 3 次事件，避免短期
重复。原模组实际每秒调用一次事件动作，本次明确沿用“每秒”强度，而不是按源码
注释误解为每个 server tick 执行。

| 稳定 ID | 显示名 | 时长 | 适配后的核心行为 |
|---|---|---:|---|
| `monster_rampage` | 怪物狂潮 | 瞬时 | 每名有效玩家周围生成 3 只随机敌对生物。 |
| `diamond_gift` | 钻石馈赠 | 瞬时 | 每人获得 15 钻石，背包满时掉在脚边。 |
| `diamond_blessing` | 钻石祝福 | 120s | 挖钻石矿为所属 DDI 目标恢复 1 心，不超过上限。 |
| `diamond_curse` | 钻石诅咒 | 瞬时 | 汇总该 objective 全部成员持有的钻石数，按现有生命上限截断后一次结算。 |
| `eclipse_curse` | 日食诅咒 | 瞬时 | 施加 60 秒失明。 |
| `calm` | 平静 | 瞬时 | 无事发生。 |
| `cloud_effect` | 唉，云朵？ | 瞬时 | 施加 30 秒漂浮。 |
| `food_rain` | 美食雨 | 10s | 每人每秒掉落 1–2 个食物。 |
| `xp_storm` | 经验风暴 | 10s | 每人每秒生成约 6 个经验球。 |
| `life_blessing` | 生命赐福 | 10s | 施加生命恢复 II。 |
| `ore_underfoot` | 脚下出矿 | 10s | 临时替换安全方块，结束或中止时恢复。 |
| `anvil_storm` | 铁砧暴雨 | 10s | 每人每秒生成 2–3 个只属于本事件的掉落铁砧。 |
| `tnt_rain` | TNT 降雨 | 瞬时 | 每人上空生成 20 个点燃 TNT。 |
| `cave_in` | 地底塌陷 | 30s | 破坏脚下 3×3 可破坏方块。 |
| `pumpkin_head` | 全员南瓜头 | 60s | 保存并临时替换头盔，任何停止路径均恢复。 |
| `inventory_shuffle` | 物品栏洗牌 | 瞬时 | 打乱快捷栏。 |
| `chicken_rain` | 小鸡天降 | 瞬时 | 每人生成 50 只带事件标签的小鸡。 |
| `player_swap` | 玩家互换位置 | 瞬时 | 连同维度安全交换位置。 |
| `fire_trail` | 脚步生火 | 30s | 脚步生成的火焰会被记录并清理。 |
| `cage_trial` | 囚笼试炼 | 10s | 临时铁栏笼，5 秒后生成苦力怕，结束恢复。 |
| `sky_water_challenge` | 高空落水挑战 | 30s | 每队一名代表高空落水，成功为该目标恢复 1 心。 |
| `crop_speed_grow` | 作物速成 | 15s | 催熟附近作物与树苗。 |
| `durability_blessing` | 豁免祝福 | 120s | 玩家物品不消耗耐久。 |
| `equipment_rust` | 装备锈蚀 | 120s | 玩家物品耐久损耗变为 5 倍。 |
| `hunger_disease` | 饥饿疫病 | 30s | 饥饿效果，食物回复减半。 |
| `inventory_migration` | 物资迁徙 | 瞬时 | 背包物品全部掉在脚边。 |
| `everyone_baby` | 全员变幼体 | 60s | 临时缩放体型、速度与跳跃，结束精确恢复。 |
| `slime_possession` | 粘液附身 | 30s | 临时替换脚下方块，结束恢复。 |
| `arrow_trial` | 箭雨试炼 | 10s | 每队代表承受箭雨；受伤扣 1 心，否则加 1 心。 |
| `trade_merchant` | 交易商人 | 30s | 每队代表附近生成临时交易商人。 |

临时方块记录必须包含世界键与位置，跳过基岩、不可破坏方块及方块实体；恢复时
只恢复仍保持本事件替换状态的位置，避免覆盖玩家后来主动放置的方块。所有事件
生成实体同时带有类型标签和对局 session 标签；除运行期 UUID 登记外，还保留
过期实体墓碑，并在实体重新加载时清除跨局残留。临时状态效果只撤销事件自己
施加的实例，不覆盖玩家原本已有的效果。高空落水会先寻找安全位置，并在失败、
断线、强制停止或对局结束时把代表玩家送回原世界和原位置后再恢复物品栏。

涉及 DDI 生命的事件只能调用 `DDIObjectiveManager` 的批量结算入口。队伍共享
模式按 objective 去重，人数较多的队伍不会因为同一全局事件多扣或多回生命；扣心
原因写入 DDI 赛后历史。

## 4. 语音关键词数据流

```text
Simple Voice Chat MicrophonePacketEvent（网络线程）
  -> 只查不可变的当前语音 objective 快照
  -> 复制 Opus 包到有界、每玩家串行队列
  -> 每玩家 OpusDecoder，48 kHz PCM 低通后 3:1 下采样到 16 kHz
  -> 空结束包 / 静音 700 ms / 最长 8 s 分句
  -> Vosk 中文有限语法识别（当前词及其别名 + [unk]）
  -> 只接受最终结果和达标置信度
  -> server.execute
  -> 复核 gameId / objectiveId / assignmentRevision / wordId / 玩家同意
  -> DDISignalKind.VOICE_KEYWORD_SPOKEN
  -> 原有统一扣心、历史、换词、HUD 同步与胜负检查
```

每次分配、清空或无惩罚重抽词条都会递增 `assignmentRevision`。异步识别返回时
必须携带开始识别时的完整快照，旧语句不能命中已经更换的新词。

语音处理约束：

- 默认关闭；玩家偏好 `ddiVoiceConsent` 默认也是关闭；
- 只有当前持有语音词且已同意的玩家音频会被处理；
- 队伍共享模式要求当前在线有效队员全部同意后才会抽到语音词；
- Opus、PCM 和完整转写不落盘、不写普通日志、不上传云端；
- 有界队列溢出时丢弃整段并重置解码器，绝不阻塞服务器 tick；
- 每个最终分句最多触发一次，并有玩家级短冷却；
- 外放、代说与录音回放无法可靠区分，属于比赛规则边界。

## 5. 模型与降级

首版使用 Vosk Java `0.3.45` 与官方中文小模型
`vosk-model-small-cn-0.22`。模型 ZIP 为 43,898,754 bytes，解压后位于：

```text
config/yet-another-minecraft-bingo/ddi/asr/vosk-model-small-cn-0.22/
```

模型首次请求时异步下载到临时文件，校验固定长度与 SHA-256
`3AF8B0E7E0F835AE9D414CE5DF580237A3CFB08D586C9FBBB0F7FF29AD5B14BA` 后进行防 Zip Slip
解压，再原子移动到正式目录。没有 Simple Voice Chat、模型未就绪、模型损坏、
Vosk native 不支持当前架构或后端发生错误时：

- Bingo/DDI 本身仍可正常启动；
- 所有语音规则从候选池排除；
- 已分配但后来失去后端/同意条件的语音词无惩罚重抽；
- 管理命令与设置页显示明确状态。

官方 Vosk Java 包覆盖 Windows x64、Linux x64 与 macOS x64；ARM 平台首版按
“后端不支持”安全降级。

## 6. 关键词与局内定制

内置词继续存放在唯一静态词源 `words_v1.json`，不再创建第二份词表。首批覆盖
协调、Bingo 与 Minecraft 常见词，例如：

```text
等一下、快一点、过来、集合、分开、帮我、救我、完成了、差一个、
棋盘、格子、倒计时、主世界、下界、末地、村庄、矿洞、堡垒、要塞、
工作台、熔炉、钻石、红石、绿宝石、下界合金、黑曜石、苦力怕、
末影人、烈焰人、恶魂、敌人、对面、回家、睡觉、传送
```

自定义关键词在运行时生成稳定 ID，只影响之后的抽取；删除一个正被使用的词时
对该 objective 无惩罚重抽。限制为每局最多 32 条，中文至少 2 个字，拉丁词至少
3 个字符，拒绝纯数字、控制字符和规范化后重复项。

已实现命令：

```text
/bingo options ddi voice enable <true|false>
/bingo options ddi voice keyword add <关键词>
/bingo options ddi voice keyword remove <关键词>
/bingo options ddi voice keyword list
/bingo options ddi voice keyword reset
/bingoprefs ddi_voice_consent <true|false>

/bingo ddi event status
/bingo ddi event trigger <event_id>
/bingo ddi event stop
/bingo ddi voice status
/bingo ddi voice model download
```

配置更改走现有权限与 `OptionsService`；运行态触发事件和下载模型只允许管理员。

## 7. v0.5 验收结果

- 原模组 30 个特殊事件已全部移植，可通过稳定 ID 选择或手动触发；正常结束、
  强制替换、局中关闭、`/bingo end` 和 scope shutdown 均进入统一清理路径；
- 最近 3 次事件不重复；设置墙提供总开关、30～3600 秒间隔、四套预设和 30 项
  分页多选，空自定义池会被配置校验拒绝；
- 队伍共享事件生命结算按 objective 一次，个人模式按玩家 objective 一次，扣血
  原因进入 DDI 赛后历史；
- 单一 `words_v1.json` 共 356 条唯一词条，其中 40 条为内置语音关键词；另支持
  每局最多 32 条自定义关键词；
- 语音规范化、关键词验证、48 kHz→16 kHz 重采样、置信度、串行有界队列、
  冷却和旧 revision 拒绝均已有自动化测试；后端或模型不可用时安全排除语音词；
- `common`、`integration-voicechat` 与 `integration-ddi` 共 87 项测试通过，
  0 failure、0 error；`:mc1.21.11:build` 正式构建成功；
- 成品：`mc1.21.11/build/libs/bingo-but-dont-do-it-1.21.11-v0.5.jar`，
  59,281,133 bytes；共 4,500 个 JAR 条目、0 个重复路径；SHA-256：
  `0BF758271C14129693F645085D571C9FFA3F2C204180FD90671AB18FBC8780B6`。
