# DDI 语音模型分发与同意机制调研

> 调研日期：2026-07-19  
> 范围：只讨论当前 DDI 的服务端 Simple Voice Chat 音频入口、服务端 Vosk 识别、模型分发/状态与玩家同意。本文不改动实现。  
> 来源限制：只使用项目当前源码，以及 Vosk、Simple Voice Chat 与 Apache License 的一手资料。凡是设计取舍均明确标为“推断/建议”，并非上游承诺。

## 结论

**不应让每位玩家下载 Vosk 模型，也不应把模型塞进主模组 JAR。** 当前链路是在服务器收到 Simple Voice Chat 的 `MicrophonePacketEvent` 后，由服务器进程加载 Vosk；该事件的官方定义就是“麦克风包到达服务器时”发出。[Simple Voice Chat API：`MicrophonePacketEvent`](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html)

当前项目也确实把 `vosk-model-small-cn-0.22` 下载、校验、解压并加载在服务器的 `config/yet-another-minecraft-bingo/ddi/asr/` 下，而不是客户端目录：[实施记录](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md#L133)、[模型管理器](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L28)。因此，玩家需要的是能连入服务器的 Simple Voice Chat；**不需要 Vosk Java、原生库或中文模型**。这一结论是由官方 API 的服务端事件语义和项目当前服务端 `Model(directory)` 调用共同推得。[Simple Voice Chat API：`MicrophonePacketEvent`](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html) [Vosk Java 模型加载位置](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L118)

推荐交付策略是：**服务端外置、版本化、校验后的模型缓存 + 自动预热 + 可离线预置**。去掉玩家手输命令，但保留一次性的、清晰的点击同意；拒绝者不能悄悄绕过语音挑战，应在开局前选择退出该局或观战。

## 已核验的事实

### 音频与模型的位置

| 事项 | 已核验结论 |
| --- | --- |
| 语音包到哪里 | Simple Voice Chat 的 `MicrophonePacketEvent` 由服务器在麦克风包到达时触发；它同时是 `ServerEvent`。[官方 Javadoc](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html) |
| Vosk 是否可离线 | Vosk 官方将其定义为离线语音识别工具包，并提供 Java binding。[Vosk 官方仓库](https://github.com/alphacep/vosk-api) |
| 当前 DDI 的模型位置 | 当前代码将模型根目录设为服务器配置目录；异步下载后以该目录创建 `Model`。这是项目当前源码事实。[模型管理器](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L28) [加载调用](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L118) |
| 当前 DDI 的下载安全措施 | 已有固定 ZIP 长度和 SHA-256 校验、临时目录、解压前路径限制及原子安装；失败会保留为后端不可用而不是阻塞游戏。这是当前源码事实。[模型下载、校验与安全解压](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L148) |
| 当前状态是否有真实进度 | 没有。公开状态只有阶段、语音服务是否可用和稳定诊断码；下载循环虽维护了 `copied` 字节数，但没有发布到状态对象。因此现有命令无法显示百分比。这是当前源码事实。[状态类型](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordBridge.kt#L13) [下载循环](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L191) |

### Vosk 模型与许可证

当前选用的 `vosk-model-small-cn-0.22` 在 Vosk 官方模型表中标为 42 MB、轻量中文模型、**Apache 2.0**；同表将 1.3 GB 的 `vosk-model-cn-0.22` 标为面向服务端处理的大模型。Vosk 也说明小模型通常约 50 MB、运行约需 300 MB 内存；这些是模型选择和服务器预热容量评估的依据，而不是实际 DDI 性能承诺。[Vosk 官方模型表](https://alphacephei.com/vosk/models)

项目当前锁定 Vosk Java `0.3.45`；该版本存在于上游官方发布页。[项目依赖锁定](gradle/libs.versions.toml) [Vosk `v0.3.45` 发布](https://github.com/alphacep/vosk-api/releases/tag/v0.3.45) Vosk API 官方仓库标注为 Apache-2.0。[Vosk 官方仓库](https://github.com/alphacep/vosk-api)

Apache-2.0 允许复制、修改、再分发和再许可；再分发时必须给接收者附上许可证副本，并保留适用的版权、专利、商标和署名通知；若原作品带有 `NOTICE`，还须按许可证条件保留其中适用的署名。许可证并不授予对上游商标的任意使用权。[Apache License 2.0：授权](https://www.apache.org/licenses/LICENSE-2.0) [Apache License 2.0：再分发与商标](https://www.apache.org/licenses/LICENSE-2.0)

**分发含义（推断/建议，不构成法律意见）：**

- 无论将该模型放进模组、独立服务端扩展还是整合包，都应随发布物提供 `THIRD_PARTY_NOTICES`（或等价可访问页面），至少列出 `vosk-api`、`vosk-model-small-cn-0.22`、来源链接和 Apache-2.0 全文/链接；发布前还应检查下载 ZIP 内是否另带 `NOTICE`。
- 不要把“所有 Vosk 模型都可同样再分发”当作规则。官方模型页明确列出 Apache、AGPL、LGPL、MIT、CC-BY-NC-SA 等不同许可证；这里的结论只覆盖当前的 `small-cn-0.22`。[Vosk 官方模型表](https://alphacephei.com/vosk/models)

## 四种分发方式比较

下表的“体积”“升级”和“体验”是基于已核验的 42 MB 模型、服务端识别位置和 Apache-2.0 条件做出的**工程推断**；并非 Vosk 或 Simple Voice Chat 的官方功能声明。

| 方案 | 可维护性 | 首次体验 | 离线部署 | 体积与升级 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 主模组内置模型 | 低：模型更换会和代码版本绑定，第三方通知也进入主发布物 | 无网络等待，但若主模组也发给客户端，会让每位客户端获得不需要的约 42 MB 资产 | 好 | 主 JAR 明显变大；任何模型补丁都要求更新 JAR | 不推荐 |
| 服务端首次下载、缓存、校验、显示进度 | 高：要维护清单、校验、状态和失败恢复；但模型可独立升级 | 第一个启用服务器需等待一次，之后复用服务器缓存；玩家无需等待下载 | 初始下载依赖网络，可用预置解决 | 主 JAR 保持小；模型按清单独立更新 | **推荐的默认路径** |
| 独立可选的服务端语音扩展 JAR | 中：将 Vosk/SVC 兼容矩阵和发布节奏从主模组分离，但多一个安装物 | 服务器管理员多装一个 JAR；客户端仍无需模型 | 取决于扩展是否仍采用外置缓存或预置模型 | 代码可选，模型仍应外置 | 适合未来把“语音挑战”做成可选产品线；现在不必为了模型而拆 |
| 整合包/服务器发行包预置模型 | 中：发行包必须随模型升级 | 最好：开服即用，玩家无需模型下载 | 最好 | 服务器发行包增加约 42 MB；若把服务端模型误放进所有客户端包，会造成无意义膨胀 | **推荐作为无网/赛事部署的备用路径** |

官方模型页将小模型定位为移动/桌面等轻量场景、将大模型定位为高精度服务器转写；这支持继续采用当前小中文模型和“服务器仅存一份”的方向，但并不能保证关键词识别准确度。[Vosk 官方模型说明](https://alphacephei.com/vosk/models)

## 推荐的模型交付方案

### 1. 服务器模型清单与外置缓存

建议保持模型在当前服务器配置目录，而非 JAR 内；增加一个由代码发布的不可变清单，至少包含：

```text
modelId                 vosk-model-small-cn-0.22
modelVersion            0.22
archiveBytes            43,898,754
sha256                  <完整 SHA-256>
officialSource          https://alphacephei.com/vosk/models/...
license                 Apache-2.0
layoutVersion           1
```

其中模型 ID、大小和许可来自官方模型表；精确 ZIP 字节数与 SHA-256 应继续由项目在发布时固定并验证。[Vosk 官方模型表](https://alphacephei.com/vosk/models) [当前固定长度与 SHA-256](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L35)

建议的服务器启动/大厅预热流程（**推断/建议**）：

```text
管理员启用“语音挑战”
  → 服务端预检平台、缓存和清单
  → 已安装且校验通过：后台加载模型
  → 缺失且 AUTO_DOWNLOAD：服务器下载、校验、解压、原子安装、加载
  → 缺失且 REQUIRE_PREINSTALLED：显示“需预置模型”，不进入下载
  → 任一步失败：本局从候选池排除语音词，不影响其他 DDI
```

不要在玩家已经抽到语音词或比赛正在进行时才开始首次下载。当前实现已在后端不可用时排除语音词；将下载提前至大厅/开局预检，只是把失败和等待移到公平性更好的时间点。[当前降级规则](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md#L142)

### 2. 离线与镜像策略

建议提供两个正式的管理员路径（**推断/建议**）：

1. `AUTO_DOWNLOAD`：只由服务器管理员配置的官方 URL 下载，校验固定哈希后安装。
2. `REQUIRE_PREINSTALLED`：服务器发行包或管理员手工放入已校验的模型目录；启动时只验证和加载，绝不联网。

可选镜像也只能由管理员配置，并且必须与清单的 SHA-256、长度和目录布局完全一致。普通玩家、聊天命令和网络包都不能指定 URL 或文件路径；这既避免把服务器变成任意下载器，也保证识别模型的来源可审计。当前实现已做长度、哈希和安全解压，因此这项建议是扩展现有边界，而非改为客户端下载。[当前下载校验](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L191)

### 3. 为什么不是“单独模型模组包”

单独的**服务端**语音扩展 JAR 可以成为将来的发布边界，但它不能解决模型更新本身：JAR 内置模型仍然会耦合代码与 42 MB 资产；JAR 不内置模型则仍需外置缓存或预置发行包。

因此近期最小且可维护的组合是：主模组保留现有可选集成、模型外置缓存；为赛事提供一个带预置模型的**服务器发行包**。只有当大量服务器永远不用语音挑战、或 Vosk/SVC 的依赖发布节奏开始频繁干扰主模组时，再把整个语音能力拆成可选服务端扩展。这是架构建议，不是由上游 API 强制的结论。

## 安全的下载状态与进度展示

### 服务器权威状态

建议将模型状态定义为只含运维信息的快照（**推断/建议**）：

```text
phase: MISSING | DOWNLOADING | VERIFYING | EXTRACTING | LOADING | READY |
       FAILED | UNSUPPORTED
modelId, modelVersion
bytesDownloaded, expectedBytes, percent
attempt, startedAt, updatedAt
stableErrorCode
```

`percent` 仅在 `expectedBytes > 0` 时计算；`VERIFYING`、`EXTRACTING`、`LOADING` 不伪造百分比，而显示阶段。下载字节数应从实际写入的字节累计，而不是不可信的 HTTP `Content-Length` 推断。当前代码已经以固定期望长度防止超量/不完整下载，且状态对象已承诺诊断码不含识别文本；扩展时应保持这一性质。[当前长度核验](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L202) [当前状态隐私约束](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordBridge.kt#L26)

### 管理员与玩家分别看什么

| 受众 | 应看到 | 不应看到 |
| --- | --- | --- |
| 管理员/控制台 | 模型 ID、阶段、`已下载/总字节`、百分比、开始/更新时间、是否使用预置、稳定错误码、平台支持情况 | 音频、PCM、完整/部分转写、关键词命中记录、任意玩家的麦克风活动 |
| 参赛玩家 | “服务器语音识别准备中（37%）”“已就绪”“本局不会抽语音词”的简短状态；仅在阶段变化或进度跨 5% 档时更新 | 下载 URL、完整哈希、堆栈、服务器文件路径、其他玩家的授权/连接/识别状态 |

这是数据最小化与反刷屏的产品建议。它和当前设计“不落盘音频和完整转写、不写普通日志”的边界一致。[当前隐私约束](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md#L123)

首版可先在现有管理员命令中增加真实进度，并向所有参赛者广播节流后的文字状态；不必为了进度另做客户端模型下载器。若已有 DDI 客户端同步通道，后续可把同一份无敏感信息的状态快照渲染成小型 HUD，而服务端命令仍应是权威诊断入口。当前命令已经展示后端状态与无文本的流水线诊断，可自然承载该扩展。[当前诊断命令输出](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDICommands.kt#L317)

## 玩家同意机制比较

必须先纠正文案：这里不是“玩家本机离线识别”，而是**服务器本地离线识别**。Simple Voice Chat 的麦克风包已到达服务器，Vosk 也在服务器配置目录加载；虽然当前设计不保存音频或转写，玩家仍应清楚处理发生在服务端。[Simple Voice Chat API：`MicrophonePacketEvent`](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html) [当前模型加载](integration-voicechat/src/main/java/me/jfenn/bingo/integrations/voice/VoiceKeywordModelManager.kt#L118) [当前不落盘约束](DDI_SPECIAL_EVENTS_AND_VOICE_IMPLEMENTATION.md#L123)

| 方案 | 优点 | 风险 | 建议 |
| --- | --- | --- | --- |
| 默认同意、无提示 | 摩擦最低 | 玩家可能不知道语音会用于服务端关键词裁决；日后隐私说明或处理目的改变也没有重新确认点 | 不建议 |
| 首次进入/准备阶段点击“同意” | 仍只需一次点击；可把位置、用途、不上传、不保存、拒绝后果说清楚；可按政策版本重新展示 | 需要一个简单 UI 与持久化状态 | **推荐** |
| 服务器强制开启、没有拒绝路径 | 赛事规则最一致 | 会把不愿接受处理的玩家逼入“同意或不能玩”，且容易被误解为隐藏监听 | 不建议作为普通服务器默认；仅私有赛事可将“确认语音规则”作为参赛门槛，但仍应给退出/观战路径 |

推荐将现在的 `/bingoprefs ddi_voice_consent true` 改为一次性点击确认，而不是直接默认同意。建议流程（**推断/建议**）：

1. 服务器启用语音挑战后、比赛正式开始前，对参赛者展示模态提示：`服务器会对 Simple Voice Chat 已发送到服务器的语音做离线关键词识别；不会上传、保存音频或保存转写；命中可能扣除 DDI 生命。`
2. 提供“同意并准备”和“拒绝/观战”两个按钮，不使用默认选中；把确认记录为 `serverId + policyRevision`。
3. 只有全体将要参加语音挑战的成员确认后，才能把比赛置为可开始。被拒绝者不是“继续参赛但永远抽不到语音词”，否则会获得不对等的规避优势。
4. 仅当处理地点、用途、保留策略或数据接收方发生变化时提升 `policyRevision` 并重新询问；单纯同一策略下的模型哈希修补无需再次打扰玩家。

这保留了玩家选择，同时去除了手输命令的负担。也与当前团队共享模式“全体在线有效队员同意才可抽语音词”的公平性意图一致。[当前队伍资格检查](integration-ddi/src/main/kotlin/me/jfenn/bingo/integrations/ddi/DDIObjectiveManager.kt#L1116)

## 分阶段改进计划

### P0：修正认知和状态缺口

1. 将所有玩家文案从“本机离线识别”改为“服务器本地离线识别”。
2. 将 `DOWNLOADING` 细化为下载、校验、解压、加载；发布实际字节、总字节和稳定错误码。
3. 让管理员可见完整运维快照，让玩家只见经过节流的准备状态；任何路径都不暴露音频、转写、词条命中、URL 或文件路径。

### P1：将模型从“比赛时下载”变为“开局前预热”

1. 增加 `AUTO_DOWNLOAD` 与 `REQUIRE_PREINSTALLED` 两种服务器策略。
2. 在大厅启用设置或开局预检时准备模型；后端未就绪时不开启语音词，而不是中局等待。
3. 给服务器发行包提供官方模型预置说明和第三方许可证清单；保留哈希验证，即使模型来自整合包。

### P2：简化同意且不牺牲知情

1. 用一次性点击 UI 替代手动命令；不采用默认勾选。
2. 将“拒绝”定义为不加入该语音挑战对局/观战，而不是让其获取无语音词的比赛优势。
3. 以 `policyRevision` 管理重新确认，避免每次模型正常更新都打扰玩家。

### P3：再考虑可选扩展

只有当依赖体积、兼容性或发布节奏确实成为主模组的持续负担时，才把完整语音能力拆成单独的服务端扩展 JAR。无论是否拆分，模型仍应采用本报告的外置缓存/预置策略，而不是随客户端分发。

## 一手来源索引

- [Simple Voice Chat：`MicrophonePacketEvent` 官方 Javadoc](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html)
- [Simple Voice Chat API 官方入门](https://modrepo.de/minecraft/voicechat/api/getting_started)
- [Vosk 官方模型列表与许可证](https://alphacephei.com/vosk/models)
- [Vosk API 官方仓库与 Apache-2.0 标注](https://github.com/alphacep/vosk-api)
- [Vosk `v0.3.45` 官方发布](https://github.com/alphacep/vosk-api/releases/tag/v0.3.45)
- [Apache License 2.0 官方全文](https://www.apache.org/licenses/LICENSE-2.0)
