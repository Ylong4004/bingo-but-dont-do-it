# Bingo × Don't Do It 集成审计与实施计划

> 审计日期：2026-07-15  
> 审计基线：Bingo 2.9.7 集成分支，DDI 代码位于 `integration-ddi/`  
> 目标版本：先完成 Minecraft 1.21.11 / Java 21 的可运行闭环
> 当前进展：首轮启动、状态机、同步、HUD、事件桥和结算已实现；2026-07-15 新增个人/队伍共享双玩法、DDI 扣血历史结算页和 V9 结算协议，仍需双客户端运行验收。

## 1. 结论摘要

当前 DDI 集成保留了原模组的大部分词池和部分检测代码，但“插件启动、对局生命周期、事件检测、网络同步、客户端 HUD、Bingo 结算”没有形成一条完整执行链。因此 DDI 默认不会实际启动；即便只补控制器实例化，也仍然会遇到初始状态不同步、计时器不运行、跨局回调累积、部分词条永远无法触发以及客户端构建失败等问题。

本次改造采用以下原则：

1. 保留已有词池、配置项和可验证的检测逻辑。
2. 重建 DDI 的启动和对局状态机，使所有入口幂等。
3. Fabric 全局事件只注册一次，把事件路由到当前活动对局。
4. 服务端作为唯一权威状态源，客户端只保存快照并渲染。
5. 先支持 1.21.11；在事件桥稳定后再按 Minecraft 版本提供适配器。

## 2. 推荐玩法规则

建议将 DDI 定义为 Bingo 的可选附加胜利条件：

- DDI 启用时，以进入 `PLAYING` 时至少两支有效 Bingo 队伍的在线成员作为本局固定参赛名单；不足两队时本局 DDI 关闭，不产生个人假胜利。
- DDI 提供 `INDIVIDUAL` 与 `TEAM_SHARED` 两种词条归属模式；旧配置默认保持个人模式。
- 个人模式下，每名玩家拥有独立禁止词、心数和换词截止时间，可以看见其他参赛者的词条但不能看见自己的词条。
- 队伍共享模式下，按开局时的 Bingo 队伍创建状态：同队所有成员使用同一禁止词、同一倒计时和同一生命池，默认生命为 3。
- 队伍任一成员触发当前禁止词时，全队共享生命只扣一次并统一换词；生命归零后全队转为旁观并淘汰。
- 服务器不得向玩家发送其本人词条；队伍共享模式下不得向任何本队成员发送本队词条，避免从队友状态反推出自己的词条。
- 触发当前禁止词时扣一心并立即换词；倒计时结束只换词、不扣心。
- 抽到“立即扣心/立即回血”时在服务端分配阶段结算，并设置最大连续抽取次数，避免无限链。
- 心数归零后保留原 Bingo 队伍归属，但在 `PLAYING` 内强制为旁观模式，不再参与 DDI 检测；POSTGAME/PREGAME 后重新交给 Bingo 的 `PlayerController`。
- 个人模式下活动参赛者断线即淘汰；队伍模式下单个成员断线不扣共享生命，全队无人在线时按弃权将共享生命清零。
- 最后仍有存活成员的 Bingo 队伍获胜；若正常 Bingo 条件先完成，则正常 Bingo 胜利优先结束对局。

如果最终产品希望 DDI 只作为干扰机制而不决定胜负，只替换“结算适配器”即可，词条引擎、事件桥和网络协议无需重写。

队伍玩法可在开局前使用 `/bingo options ddi mode team` 启用；使用
`/bingo options ddi mode individual` 切回个人玩法。`/bingo options ddi hearts 3`
设置个人或队伍共享生命上限。

### 2.1 队伍共享玩法实施状态

- 新增可序列化的 `DDIObjectiveMode.TEAM_SHARED`，旧配置缺少该字段时保持 `INDIVIDUAL`。
- 服务端按 objective 维护状态：个人模式一人一个 objective，队伍模式一支 Bingo 队一个 objective。
- 团队状态包每队只发送一次；本队词条在服务端发送前置空，客户端也不会保留该字段。
- 队友同时触发时有 objective 级冷却保护；换词会重置全队成员的检测进度。
- 队员离开开局队伍后退出本局检测；团队仍有有效成员则继续，最后成员离开或全队离线时整队弃权。
- 当前词条、共享生命和倒计时仍属于运行时内存状态；服务器在 `PLAYING` 中途重启不会恢复精确进度，这是后续持久化阶段的已知限制。

### 2.2 结束、词条可见性与扣血历史

- `/bingo end` 进入与正常 Bingo 结束相同的 `GameService.end -> POSTGAME` 链路。结束信息会先快照，随后 DDI session 停止、计时和检测清理，并向客户端发送 reset 以移除对局 HUD。
- `/bingo resume` 会在同一个 Bingo game id 下开始一段新的 DDI session，重新分配词条并恢复满生命；当前不会恢复结束前的 DDI 生命、词条和计时。
- 服务端按查看者过滤 objective：普通玩家收到自己的个人/本队生命和计时，但自己的个人/本队词条文本为空；其他队伍的词条文本可见。旁观者可以看到所有队伍词条。客户端再次丢弃本队文本，作为第二层保护。
- 有效触发扣血后，刚刚导致扣血的旧词条会通过最近触发通知展示；换词计时到期不会被当成扣血记录。
- 每次真正执行 `loseHeart()` 后，服务端按 Bingo 队记录词条、触发玩家（若有）以及剩余/最大生命。即时扣心词条也会记录，但标记为系统触发。
- 结算信息在 DDI 清理前深拷贝历史。新版客户端通过 `game_over_v9` 接收，并在结束面板新增“不要做”页签，逐队显示所有导致扣血的词条；没有扣过血的参赛队也会显示“本局没有因词条扣血”。旧 V8 及以下客户端可继续兼容结算，但不会显示该页签。
- 当前历史不设条数上限；极端超长对局可能增大存档和结算包，这是后续可增加上限或分页的 P2 项。

## 3. 当前问题审计

### 3.1 P0：服务端插件没有被实例化

- `DDIModule.kt` 只用 `scopedOf(::DDIGameController)` 声明了惰性 scoped 定义。
- `DDIEntrypoint.kt` 只调用 `loadModules`，没有从 Bingo scope 解析控制器。
- `CommonModule.kt` 的 `Scope.commonInit()` 没有 `get<DDIGameController>()`。
- `DDIGameController` 构造阶段才会注册 S2C payload、向 manager 注入发送器并监听游戏状态；控制器不创建时，这些逻辑全部不会发生。

### 3.2 P0：客户端模块、网络和 HUD 没有启动

- 客户端入口只加载通用 client/common 模块，没有 DDI client Koin 模块。
- 项目内没有定义 `ddiClientModule`，也没有创建 `DDIClientController`。
- 自定义 payload receiver 和 HUD render listener 都在 `DDIClientController` 构造时注册，因此当前 HUD 不会工作。
- `integration-ddi` 使用 Loom split environment source sets，但原构建脚本没有把 `platform`、`common` 的 client 输出放进 DDI client 编译类路径。
- `DDIHudRenderer` 声明 `MutableList<IText>`，实际加入 Minecraft `Text`，与 `IDrawService` 的平台文本接口不匹配，是确定的客户端编译错误。

### 3.3 P0：ServiceLoader 描述符可能在打包时丢失

`integration-waystones` 和 `integration-ddi` 都提供：

```text
META-INF/services/me.jfenn.bingo.plugin.IBingoInternalPlugin
```

各 Minecraft 模块通过 Shadow 同时打入两个集成模块，但原配置没有合并服务描述符。Shadow 的默认重复文件策略会保留先遇到的文件并丢弃后续 provider，因而即使 DDI 模块本身正确，最终 JAR 仍可能没有 DDI provider。

基线修复要求：

- 对 `META-INF/services/**` 使用 `mergeServiceFiles()`。
- 只对服务描述符允许重复输入，其他资源继续保留原有排重策略。
- 构建后解包检查同一个 provider 文件包含 Waystones 和 DDI 两行实现类。

### 3.4 P0：对局启动路径不完整

- 控制器仅在 `COUNTDOWN` 状态启动 DDI。
- Bingo 在倒计时关闭或为零时可以从 `LOADING` 直接进入 `PLAYING`。
- scope 初始化时可能重放当前 `PLAYING` 状态，现有控制器同样不会启动。
- 检测器在倒计时阶段提前记录潜行、朝向等边沿状态；真正进入游戏时，已经处于该状态的玩家必须先离开再进入才可能触发。

应改为：在 `PLAYING` 执行 `ensureStarted(gameId)`，并允许重复调用而不重复初始化；`POSTGAME`、`PREGAME` 和 scope stop 都执行无条件、幂等的 `stop()`。

### 3.5 P0：初始同步和词条计时器没有调用点

- `startGame()` 只清理状态、分配词条和注册检测器。
- `syncPlayer()` 没有调用者，开局不会向客户端发送词条、心数和计时。
- `tickWordTimers()` 没有调用者，词条不会按时更换。
- 触发扣心和换词后也没有一个统一的“状态变更 → revision 增加 → 网络同步”出口。

### 3.6 P1：Fabric 全局回调跨局累积

`DDITriggerDetector.register()` 每局向 Fabric 全局 Event 注册新 lambda；`unregister()` 只能清 Map 和重置布尔标记，Fabric Event API 不会因此撤销 lambda，而且旧回调没有检查该标记。结果包括：

- 下一局再次注册同一组回调。
- 每局事件处理次数递增，可能一次行为扣多颗心。
- integrated server 关闭后，全局回调仍持有旧 scope、manager 和 server，形成内存泄漏。

应把 Fabric 回调移入进程级桥接器，只安装一次；桥接器按 `MinecraftServer` 查找当前活动 session，无 session 时直接返回。

### 3.7 P1：检测逻辑错误和性能风险

- 偏航角 `225..315` 被额外映射到 `LOOK_WEST`，并与正常东西方向判断重叠。
- 朝向、封闭、入水、浮空、头顶方块等边沿状态只写入 `true`，离开状态时没有写回 `false`，通常每局只能触发一次。
- 安山岩、闪长岩、花岗岩、凝灰岩的“站立”检测复用了 `MINE_*` 映射，真正的 `STAND_ON_*` 不会触发。
- 经验进度先被覆盖再与“旧值”比较，普通经验增长不会触发 `GAIN_EXPERIENCE`；目前只有升级分支可能顺带触发。
- 放置检测发生在使用方块回调结果确定前，交互容器或放置失败也可能被算作放置。
- 同一物理事件可连续产生多个候选触发；第一次换词后，后续候选可能命中新词并再次扣心。
- 当前每 tick 对每名在线玩家扫描几乎所有条件，包含多次背包遍历、玩家两两距离计算和从头顶扫描至世界高度上限，存在明显 TPS 风险。

推荐只评估玩家“当前词条”所需的检测器。事件型词条由桥接器推送候选事件；持续型词条只维护当前词条所需的少量计时状态。

### 3.8 P1：参赛名单、重连和胜负没有接入 Bingo

- 当前直接使用 `server.playerManager.playerList`，会包含管理员、旁观者和非参赛玩家。
- 没有 late join、channel register、断线或重连同步。
- 离线但仍标记存活的玩家可能阻塞胜负判定。
- “最后幸存者”逻辑只广播消息，不冻结 DDI，也不结束 Bingo；胜者之后仍可能继续扣心。
- `ObjectiveData.DDI` 和 `BingoObjective.DDIWordEntry` 没有实际消费者，所谓 Bingo 计分集成尚未完成。
- DDI 淘汰只设置内部布尔值，没有切换为 Bingo 旁观者。

### 3.9 P2：客户端展示和配置体验不完整

- 当前实现只渲染自己的词，和原版“额头牌”规则相反。
- `otherPlayers` 和 `recentTriggers` 虽被写入，但 HUD 没有渲染它们。
- 客户端没有 tick 通知寿命和词条倒计时。
- 断线或换服时没有本地 reset，若服务器 reset 包未到达会残留旧 HUD。
- 词条和提示大量硬编码为中文，未使用 Bingo 的语言键系统。
- DDI 仅有命令配置，没有接入大厅菜单；配置文件值也缺少统一范围校验。

## 4. 基线中阻塞随机池的 31 类词条

严格静态比对基线得到：词池共有 171 个条目、169 种触发类型；其中 **30 种触发类型在旧 `DDITriggerDetector` 中完全没有引用**。另有 `GAIN_EXPERIENCE` 虽有引用，但旧进度在比较前已被覆盖。当前 1.21.11 实现已经加入合成、拾取、丢弃、容器和交易 mixin bridge，修正特殊方块站立和经验检测；两个即时词条由服务端分配器结算。因此当前不再过滤这些词条，但必须通过 mixin 运行测试确认每条成功路径。

| 类别 | 数量 | 触发类型 |
| --- | ---: | --- |
| 合成结果 | 10 | `CRAFT_CRAFTING_TABLE`, `CRAFT_WOODEN_PICKAXE`, `CRAFT_STONE_PICKAXE`, `CRAFT_IRON_PICKAXE`, `CRAFT_WOODEN_AXE`, `CRAFT_STONE_AXE`, `CRAFT_IRON_AXE`, `CRAFT_WOODEN_SWORD`, `CRAFT_STONE_SWORD`, `CRAFT_IRON_SWORD` |
| 丢弃行为 | 9 | `DROP_ITEM`, `DROP_DIRT`, `DROP_COBBLESTONE`, `DROP_COBBLED_DEEPSLATE`, `DROP_ANDESITE`, `DROP_GRANITE`, `DROP_DIORITE`, `DROP_TUFF`, `DROP_WOODEN_PICKAXE` |
| 拾取行为 | 3 | `PICKUP_ITEM`, `PICKUP_WOOD`, `PICKUP_DIAMOND` |
| 特殊方块站立 | 4 | `STAND_ON_ANDESITE`, `STAND_ON_DIORITE`, `STAND_ON_GRANITE`, `STAND_ON_TUFF` |
| 容器与交易 | 2 | `OPEN_CONTAINER`, `VILLAGER_TRADE` |
| 分配时即时效果 | 2 | `INSTANT_LOSE_HEART`, `INSTANT_GAIN_HEART` |
| 经验增长（引用存在但主要路径损坏） | 1 | `GAIN_EXPERIENCE` |

原 `Dont_do_it` 模组通过 mixin 覆盖合成结果槽、玩家丢弃、物品拾取、容器和交易等行为；当前实现已为 1.21.11 移植最小 mixin。后续版本仍应优先使用可靠的 Fabric 成功后事件，API 没有对应事件时再提供按版本隔离的最小 mixin。

## 5. 目标架构

```text
进程级 Fabric 事件桥（每种事件只注册一次）
                    │
                    ▼
        DDI Session Controller（Bingo scope / gameId）
                    │
                    ▼
       纯状态引擎（词、心数、期限、淘汰、revision）
             │                         │
             ▼                         ▼
   当前词条所需检测上下文          网络快照 / 增量事件
                                           │
                                           ▼
                                  客户端状态仓库与 HUD
```

建议职责划分：

- **Plugin bootstrap**：只负责加载模块和在正确生命周期创建控制器。
- **Session controller**：对齐 Bingo 状态、冻结配置和名单、启动/停止 session。
- **Pure engine**：不依赖 Fabric 全局事件，处理分配、触发、计时、心数、淘汰和胜负候选。
- **Version event bridge**：把 1.21.11 的 Fabric 事件/mixin 转为稳定的 DDI domain event。
- **Network service**：发送带 `gameId`、`revision` 的 snapshot/delta，不由 manager 持有可空 lambda。
- **Client store/HUD**：只消费协议，负责本地倒计时展示和安全 reset。

## 6. 分阶段实施计划

### 阶段 0：建立可构建、可检查的 1.21.11 基线

- 使用 Java 21 构建。
- 修复损坏的 Waystones Gradle 缓存并刷新依赖。
- 补齐 `integration-ddi` 的 split client source-set 依赖。
- 合并 Shadow ServiceLoader 描述符。
- 修正已有 DDI client 源码编译错误。
- 检查 remap JAR 中 provider 文件、DDI client class 和 payload class 均存在。

### 阶段 1：接通服务端和客户端启动链

- 服务端在 Bingo scope 创建后解析 `DDIGameController`，或给 `IBingoInternalPlugin` 增加明确的 `onScopeStarted(scope)` 生命周期。
- 客户端增加进程级 DDI module，立即创建 `DDIClientController`。
- payload codec 必须在连接建立前注册，且进程内只注册一次。
- 控制器创建后主动与当前 Bingo 状态协调，不能只等待未来状态事件。

### 阶段 2：重建对局生命周期

- 引入 `Inactive / Active(gameId, roster, config) / Completed`。
- 在 `PLAYING` 上执行幂等 `ensureStarted`，覆盖有倒计时、无倒计时和状态重放。
- 在 `POSTGAME`、`PREGAME`、scope stop 执行无条件幂等清理。
- 开局冻结参赛名单和 DDI 配置；局中配置命令仅影响下一局。
- 断线立即 DDI 淘汰；重连恢复淘汰快照。服务器在已是 `PLAYING` 的存档上启动时，若 scope 创建当下不足两支在线队伍，则本局 DDI fail closed，不静默重抽整局状态。（已实现）

### 阶段 3：权威同步和计时

- 开局发送完整 snapshot；换词、扣心、淘汰发送 revision delta。
- 玩家 channel register/重连后发送最新 snapshot。
- 服务端保存 `expiresAtTick`，到期时权威换词；客户端按截止时间本地显示秒数，并接受周期校时。
- 所有状态修改都经过一个统一事务出口，禁止“修改状态但忘记同步”。
- 协议按 viewer 过滤，绝不向玩家发送自己的禁止词。

### 阶段 4：重建事件检测层

- Fabric 回调只安装一次，并根据 server/session 路由。
- 只运行当前词条对应的事件或持续条件检测。
- 补齐 30 个完全缺失的触发类型和经验进度检测。
- 修正方向区间、边沿状态复位、特殊方块站立映射。
- 放置、合成、拾取、丢弃、交易等只在操作成功后产生 domain event。
- 为一次物理动作附加 action token，保证每名玩家每次动作最多结算一次。
- 对即时词条设置有界重抽；换词时重置仅与旧词有关的进度状态。

### 阶段 5：接入 Bingo 队伍、旁观和结算

- 从至少两支 Bingo 有效队伍的在线成员生成固定 roster，而不是所有在线玩家。（已实现）
- 个人零心时保留队伍信息并强制旁观；断线按淘汰处理，重连恢复淘汰快照。（已实现，待运行验证）
- 只在活动 roster 和同一世界内做玩家距离判断；不足两人时相关词条不应成立。
- 最后一支存活队伍通过公共结算桥标记 `TeamWinner`、发出 `TeamWinnerEvent` 并调用标准 `GameService.end`。（已实现）
- DDI 使用可序列化结束原因；结果延迟至下一 game tick 结算，让先注册的 `ScoredItemCheck` 完成本 tick 计分。若正常 Bingo 已产生赢家，则保留原 Bingo 赢家并以 Bingo 结果结束。（已实现，待同 tick 竞争测试）

### 阶段 6：完成 HUD、配置和本地化

- 主 HUD 显示其他玩家词条；自己的区域只显示心数、时间和淘汰状态。
- 实现通知列表寿命、倒计时 tick、多人分页/裁剪。
- join、disconnect、换服、reset 包都能清理客户端状态。
- 将词条和提示迁移到语言键，网络优先发送稳定 word ID。
- 把 DDI 开关、心数和换词时间加入大厅菜单并统一校验范围。

### 阶段 7：测试、性能和版本扩展

- 为纯状态引擎写单元测试，为生命周期、协议和事件桥写集成测试。
- 建立词池覆盖测试：所有随机词必须有 provider 或 instant evaluator。
- 进行 8/32 玩家性能分析，消除全词池逐 tick 扫描。
- 1.21.11 验收后，再按版本组拆分 event adapter 并逐个启用旧版构建。

## 7. 版本策略

当前 `settings.gradle` 只启用了 `mc1.21.11`，而 `integration-ddi/gradle.properties` 也固定 Minecraft 1.21.11 / Java 21。这适合作为第一阶段目标。

仓库仍保留多个旧版 `mc*` 模块；`bingo.mod.gradle` 一旦重新启用这些模块，会把 `integration-ddi` 无条件 shade 进每个产物。DDI 当前直接引用 1.21.11 的 Minecraft/Fabric API，因此不能把同一份字节码直接发布到旧版本。

推荐策略：

1. 1.21.11 完成全链路并通过验收。
2. 把 pure engine、协议模型和玩法规则留在版本无关 core。
3. 把 Minecraft/Fabric 类型限制在按版本分组的 event/network adapter。
4. 未完成适配的 `mc*` 产物明确排除 DDI，而不是打包后等待运行时崩溃。
5. 每启用一个版本，都执行同一套词条覆盖和双客户端验收矩阵。

## 8. 验收矩阵

| 范围 | 场景 | 通过条件 |
| --- | --- | --- |
| 构建 | clean build / remap | Java 21 下所有启用模块编译；最终 JAR 的 ServiceLoader 文件包含全部 provider |
| 启动 | 正常倒计时 | 进入 `PLAYING` 时只创建一个 DDI session |
| 启动 | 零倒计时、状态恢复 | 直接 `PLAYING` 仍初始化；重复状态事件不重复注册或重发整局初始化 |
| 生命周期 | 连续三局 | 每个物理事件每局只处理一次，回调数量和内存持有不随局数增长 |
| 协议 | 两个客户端 | A 能看到 B 的词；A 的入站网络数据中不存在 A 自己的词，反之亦然 |
| 同步 | 开局、触发、到期 | 心数、词条、淘汰状态和截止时间立即一致，旧 revision 不覆盖新状态 |
| 重连 | 断线与 late join | 活动玩家断线即淘汰；重连恢复淘汰快照；非 roster 玩家只旁观；离线玩家不永久阻塞胜负 |
| 词池 | 171 条目 | 每条都有事件 provider 或即时 evaluator，不可达词不会被随机抽取 |
| 边沿检测 | 进入→离开→再进入 | 潜行、朝向、入水、封闭等每次有效边沿均可再次触发 |
| 操作检测 | 成功与失败 | 仅成功放置、合成、拾取、丢弃和交易触发；同一动作最多结算一次 |
| 结算 | 个人淘汰、最后队伍 | 淘汰者保留队伍但进入旁观；仅剩一支有存活成员的队伍时触发正式 GameOver 并冻结 DDI |
| 竞态 | 同 tick Bingo/DDI 达成 | 下一 game tick 先完成 Bingo 计分；已有 Bingo winner 时不得被 DDI winner 覆盖 |
| 性能 | 8/32 玩家 | 无逐玩家全词池扫描、无向世界顶逐格扫描、无无条件重复背包扫描 |
| 客户端 | 断线、换服、reset | 本地状态立即清空，HUD 和通知不会残留 |

## 9. 建议的首个可玩里程碑

第一批实现已经覆盖阶段 0～5 的主要代码路径：1.21.11 两端启动、幂等 lifecycle、初始/重连 snapshot、扣心换词、权威计时、完整事件桥、个人旁观和正式队伍结算。词池保持完整，不再临时过滤；当前里程碑的重点转为统一编译、mixin 注入检查和双客户端连续三局验收。

在首个里程碑完成前，不应把“控制器能被创建”视为玩法已可用；最低完成定义是两客户端能够连续运行三局，且每局都满足隐私、同步、计时和单次结算约束。

## 10. 2026-07-15 发布候选实现与复核结果

本节记录本轮实际落地结果；如与前文的“待实现”描述冲突，以本节为准。

### 10.1 生命周期、队伍与结算

- DDI 服务端控制器已由内部插件的 scope 生命周期主动创建和关闭，客户端模块也在客户端入口主动加载。
- `/bingo end` 进入 `POSTGAME` 时会停止 DDI、撤销当前服务器到 detector 的路由、清理权威状态并向客户端发送 reset。
- 支持 `INDIVIDUAL` 与 `TEAM_SHARED`：队伍模式下，同一 Bingo 队伍只创建一个 objective，队员共享词条、倒计时和生命；默认生命为 3。
- 启用 DDI 后，开局会校验生命范围 `1..20`、计时范围 `10..600`，并要求至少两支有在线成员的 Bingo 队伍；不满足时拒绝开局并返回明确提示。
- 最后一支存活队伍会通过 Bingo 的标准结束服务结束整局；结束面板使用 `game_over_v9` 展示每支队伍的 DDI 扣血历史（词条、触发者和扣血后生命）。
- 客户端网络投影按 viewer 过滤：玩家收到其他 objective 的词条，但自己的词条字段为空；同队共享模式下，队员不会收到本队词条。

### 10.2 已补充的指令

| 指令 | 作用 | 限制 |
| --- | --- | --- |
| `/bingo options ddi` | 查看 DDI 开关、模式、生命和计时 | 需配置游戏权限；可在准备阶段和游戏中查询 |
| `/bingo options ddi enable [true\|false]` | 开关 DDI | 仅准备阶段；修改后显示完整配置 |
| `/bingo options ddi mode individual` | 每名玩家独立词条与生命 | 仅准备阶段 |
| `/bingo options ddi mode team` | Bingo 队伍共享词条与生命 | 仅准备阶段 |
| `/bingo options ddi hearts <1..20>` | 设置最大生命 | 仅准备阶段 |
| `/bingo options ddi timer <10..600>` | 设置换词秒数 | 仅准备阶段 |
| `/bingo ddi status` | 查看会话、人数、objective、生命和计时，不泄露词条 | 默认仅 OP |
| `/bingo ddi status reveal` | 管理员显式查看所有词条 ID、文案和 trigger | 默认仅 OP；会揭示隐藏信息 |
| `/bingo ddi sync [player]` | reset 后重新发送当前权威投影，可修复 HUD 不同步 | 默认仅 OP；无参数同步全部在线玩家 |

没有加入强制触发、指定词条、加血或跳过词条等会破坏比赛公平性的管理命令。

### 10.3 词条覆盖与触发语义复核

- 词池共 171 条、171 个唯一 ID、169 种触发类型；169 种枚举均至少有一个词条，静态 provider/即时结算复核无裸缺失类型。
- 成功进食改由 `consumeItem` 完成路径上报，取消进食不再触发。
- 跳跃改用原版 `Stats.JUMP`，走下台阶和被击飞不再计为跳跃；丢弃改用 `Stats.DROPPED`，覆盖 Q、Ctrl+Q 和背包界面 THROW。
- `STAND_STILL_5S` 与 `LOOK_SAME_DIR_5S` 使用固定五秒窗口锚点，不能用慢走或持续缓慢旋转规避。
- 持续潜行/疾跑会持续重置“未潜行/未疾跑”计时；共享队员断线会立即清理个人检测上下文，离线时间不会累积。
- 东南西北区间互斥并在离开方向后复位；敌对生物改按 `Monster` 判断，普通攻击只接受非玩家 `LivingEntity`。
- 下落按原版 `fallDistance` 从空中最高点计算；深板岩只接受原始 `minecraft:deepslate`，其他模组的同 path 物品/方块不会误触发原版专用词条。
- 手持类词条同时检查主手与副手；`OPEN_CONTAINER` 覆盖成功打开的方块或实体容器，`OPEN_CHEST` 只接受箱子、陷阱箱和末影箱。
- 水桶/岩浆桶除普通流体路径外，也覆盖炼药锅成功装入或取出；爆炸与火焰伤害使用原版伤害标签。
- “一次性受到 5 滴血伤害”按护甲、附魔与伤害吸收结算后的实际生命下降判断，不再使用护甲前数值。
- 原 1500 ms 墙钟冷却已改为同一 objective 的同 server tick 去重，不会吞掉新词的下一次正常操作；新词也会排除刚结束词条的同 trigger，避免看似没有换词。
- Fabric 全局回调仍只注册一次；`unregister()` 通过按服务器移除 active detector 路由停止处理，因此连续对局不会重复叠加回调。

### 10.4 大厅设置墙

- “游戏特色”页保留原有六个特色开关，并调整为三列两行；页面底部新增“不要做挑战设置……”入口，避免把 DDI 的数值选项挤进原有特色开关区域。
- DDI 使用独立详情页：可直接启用/关闭玩法，在“个人独立”与“队伍共享”之间切换，并设置最大生命和自动换词时间。
- 最大生命支持 `1..20`，提供 1、3、5 快捷值；换词时间支持 `10..600` 秒、每次增减 10 秒，提供 30、60、120 秒快捷值。
- DDI 页的返回按钮会回到“游戏特色”页；大厅设置与 `/bingo options ddi ...` 修改的是同一套权威配置。

### 10.5 验证与交付

- `:common:test :integration-ddi:test`：38 个测试，0 failure、0 error、0 skipped。
- `:mc1.21.11:build`：正式 remap 构建成功。
- 开发专用服务端启动检查：Fabric Loader 0.18.4 / Fabric API 0.140.2+1.21.11 / Java 21 成功加载；DDICommands 被创建，五个 DDI S2C payload 均完成注册，未出现 Mixin apply/injection 错误。检查在未代用户接受 Mojang EULA 的预期关口停止。
- 最终 JAR：`mc1.21.11/build/libs/bingo-but-dont--do-it-1.21.11.jar`，32,966,361 bytes，4,324 个 ZIP 条目，0 个重复路径。
- SHA-256：`BF8B7593496F8E213BA13F6DE95EBED60D0011A33B15FDC1F2E36AF0601CD4AC`。

运行要求：Minecraft 1.21.11、Java 21。推荐 Fabric Loader 0.18.4（元数据最低 0.16.9）和 Fabric API 0.140.2+1.21.11（元数据最低 0.140.0+1.21.11）。Fabric Language Kotlin 1.13.7+kotlin.2.2.21 已内嵌，无需单独安装。客户端和服务端使用同一个 JAR，并都要安装 Fabric API。

仍未在本工作区完成“两个真实客户端连续三局”的人工验收，因此实际 HUD 布局、弱网重连、与第三方 mixin 模组的冲突仍应在发布服务器上做一次最终试玩；这不影响上述编译、单测、JAR 结构和服务端初始化检查的通过结论。
