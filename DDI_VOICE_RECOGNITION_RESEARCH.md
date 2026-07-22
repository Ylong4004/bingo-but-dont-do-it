# DDI“说出违禁词就扣血”离线语音识别研究

> 调研日期：2026-07-16  
> 调研范围：Simple Voice Chat 语音入口、离线中文关键词检测/语音识别、JVM 与 Windows/Linux 部署，以及 DDI 首版落地建议。  
> 证据口径：只采用项目官方文档、官方仓库源码、许可证、官方发布物和 Maven Central。文中以 **事实**、**推断/建议** 区分可直接核验的信息与工程判断。

## 1. 结论先行

1. **技术上最匹配的首版方案是 `sherpa-onnx` 的 open-vocabulary KWS（关键词 spotting）**，而不是先做完整语音转文字。它能为每位玩家动态创建只包含当前违禁词/别名的流式关键词流，官方中英双语 3M 模型给出 160 ms 或 320 ms 的算法延迟选项，并提供当前版本的 Java API 与 Windows/Linux x64 原生 JAR。[官方 KWS 说明](https://k2-fsa.github.io/sherpa/onnx/kws/index.html) · [中英双语模型](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html) · [Java 非 Android 部署](https://k2-fsa.github.io/sherpa/onnx/java-api/non-android-java.html)
2. **但 sherpa-onnx 现成 `kws-models` 发布物存在一个发布门槛**：运行库源码明确是 Apache-2.0；本次检查的模型发布页和 `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2` 内没有找到模型权重的明确许可证文件。因此可以先做内部技术原型，但在把模型随 DDI 分发前，必须向维护者确认该模型权重及词典文件的使用/再分发许可。[代码许可证](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) · [官方模型发布页](https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models)
3. **许可证明确、最容易直接发布的备选是 Vosk `0.3.45` + `vosk-model-small-cn-0.22`**。它是 Apache-2.0、离线流式、支持 Java、支持运行时有限语法；Maven JAR 已带 Windows/Linux x64 原生库。代价是它属于受限词汇 ASR 而非专用 KWS，约 42 MB 的中文小模型官方给出的典型运行内存约 300 MB，并且动态语法不能可靠覆盖模型词典之外的 Minecraft 专名或新造词。[Vosk 官方仓库](https://github.com/alphacep/vosk-api) · [模型列表](https://alphacephei.com/vosk/models) · [Java Recognizer](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java)
4. **`whisper.cpp` 不适合首版扣血判定**：它支持中文且代码/官方 Whisper 权重许可清晰，但官方实时示例本质是滑窗重复转写，不是关键词检测；模型内存和 Java/native 版本错位也明显。它更适合作为将来的“完整转写/复核”可选后端，而不是低误报、低延迟的主判定器。[whisper.cpp 官方仓库](https://github.com/ggml-org/whisper.cpp) · [内存数据](https://github.com/ggml-org/whisper.cpp#memory-usage) · [实时示例](https://github.com/ggml-org/whisper.cpp#real-time-audio-input-example)
5. **Simple Voice Chat 的麦克风事件不能直接跑解码或识别**。在已核验的 2.5.30 运行时和当前 1.21.11/2.6.21 源码中，`MicrophonePacketEvent` 是在专用语音包处理线程上同步分发的；阻塞它会直接影响语音转发。事件回调只应复制玩家 UUID 和 Opus 数据并尝试写入有界队列；Opus 解码、重采样、KWS/ASR 必须在独立工作线程进行，扣血则切回 Minecraft 服务端线程并重新验证局内状态。[2.5.30 `Server`](https://github.com/henkelmax/simple-voice-chat/blob/2de36eb3ab9cd9ccc180a60055bdd2d54fdf3fab/common/src/main/java/de/maxhenkel/voicechat/voice/server/Server.java) · [2.5.30 `PluginManager`](https://github.com/henkelmax/simple-voice-chat/blob/2de36eb3ab9cd9ccc180a60055bdd2d54fdf3fab/common/src/main/java/de/maxhenkel/voicechat/plugins/PluginManager.java)

**推荐决策：** 先用 sherpa-onnx KWS 做性能与误报原型；把“模型许可证书面确认”设为发布门槛。若门槛未通过，则发布 Vosk 版本，并在词库加载阶段拒绝/替换 OOV（模型词典外）词。不要为了赶首版改用 whisper.cpp。

## 2. 仓库现状与版本边界

### 2.1 已确认的本仓库事实

- **事实：** `gradle/libs.versions.toml` 将编译依赖固定为 `de.maxhenkel.voicechat:voicechat-api:2.5.0`；`integration-voicechat` 以 `modCompileOnly` 引入它。
- **事实：** 目前 `SimpleVoiceEntrypoint` 只注册服务启动、停止和玩家连接事件；尚未注册 `MicrophonePacketEvent`，也没有创建 Opus decoder。
- **事实：** `fabric.mod.json` 注册了 `voicechat` 入口点，但没有把 Simple Voice Chat 声明为运行时硬依赖或建议依赖。因此 **API 编译版本固定不等于玩家实际安装的 Simple Voice Chat 运行时版本固定**。
- **事实：** 项目当前目标是 Minecraft 1.21.11 / Java 21；Simple Voice Chat 官方当前 1.21.11 分支的版本是 2.6.x，而其官方文档说明从 API 2.6.0 起 API mod id 改为 `voicechat_api`，旧版仍是 `voicechat`。[官方 Getting Started](https://modrepo.de/minecraft/voicechat/api/getting_started) · [当前 1.21.11 `gradle.properties`](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/gradle.properties)

**推断/建议：** 实现前必须明确兼容策略：

- 若只支持 2.5.x 运行时，应在元数据/文档中固定兼容范围，并测试 Minecraft 1.21.11 是否存在可用的 2.5.x 构建。
- 若支持当前 1.21.11 的 Simple Voice Chat 2.6.x，应升级编译 API，处理 mod id/版本兼容变化，并重新跑集成测试。
- 不要把本报告对 2.5.30 与 2.6.21 源码的观察误写成 API 的永恒线程契约；API Javadoc 没有承诺回调线程。

### 2.2 Simple Voice Chat 不是开源依赖

**事实：** Simple Voice Chat 仓库可查看源码，但其仓库许可证为 “All Rights Reserved”，不是 OSI 意义上的开源软件。本报告讨论的 Vosk、sherpa-onnx、whisper.cpp 是识别后端；语音入口本身仍受 Simple Voice Chat 自己的许可约束。[官方 license](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/license)

## 3. Simple Voice Chat API 2.5.0：能拿到什么、不能做什么

### 3.1 麦克风事件与 Opus 数据

**事实：** 官方 Maven 的 `voicechat-api:2.5.0` 源码定义：

- `MicrophonePacketEvent` 继承 `PacketEvent<MicrophonePacket>`，在服务端收到玩家麦克风包时触发。
- `MicrophonePacket#getOpusEncodedData()` 返回该玩家的 Opus 编码数据；`isWhispering()` 只表示耳语状态。
- `PacketEvent#getSenderConnection()` 可取得发送者连接，再由 `VoicechatConnection#getPlayer()`、`Entity#getUuid()` 得到玩家 UUID。
- 该事件可取消；官方运行时代码会据此阻止后续语音包处理。因此 DDI 只监听，**绝不能为了识别而 cancel**。

来源：[API 2.5.0 官方源码 JAR](https://maven.maxhenkel.de/repository/public/de/maxhenkel/voicechat/voicechat-api/2.5.0/voicechat-api-2.5.0-sources.jar) · [`MicrophonePacketEvent` Javadoc](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/events/MicrophonePacketEvent.html) · [事件注册文档](https://modrepo.de/minecraft/voicechat/api/registering_events)

### 3.2 官方 OpusDecoder 能力

**事实：** API 2.5.0 的 `VoicechatApi#createDecoder()` 返回官方 `OpusDecoder`：

- `decode(byte[] opusData)` 输出 `short[]`，即 16-bit PCM。
- `decode(null)` 可执行 packet-loss concealment；但 `MicrophonePacketEvent` 本身没有公开序号或显式丢包信息，DDI 无法只靠该事件准确补齐每个丢失包。
- decoder 提供 `resetState()`、`isClosed()`、`close()`；Javadoc 明确警告不关闭会造成内存泄漏。
- API 的 `AudioConverter` 只负责 `byte[]`、`short[]`、`float[]` 之间的样本表示转换，不做采样率转换。

来源：[API 2.5.0 官方源码 JAR](https://maven.maxhenkel.de/repository/public/de/maxhenkel/voicechat/voicechat-api/2.5.0/voicechat-api-2.5.0-sources.jar)

**事实：** 官方 2.5.30 和当前 2.6.21 实现都以 48 kHz、单声道、20 ms 帧创建解码器；一帧通常得到 960 个 PCM 样本。实现会优先使用可用的本地 Opus decoder，并可回退到 Java Concentus。也就是说，DDI 不需要自行再分发一套 Opus 原生库。[当前 `AudioUtils`](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/common/src/main/java/de/maxhenkel/voicechat/voice/common/AudioUtils.java) · [当前 `OpusManager`](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/common/src/main/java/de/maxhenkel/voicechat/natives/OpusManager.java)

**推断/建议：** Opus 解码器带状态，必须按玩家独占或至少保证同一玩家串行调用。玩家断开、局结束、规则关闭时 `close()`；队列溢出或音频流被截断时清空该玩家待处理音频并 `resetState()`，不要丢掉任意一个中间包后假装流仍连续。

### 3.3 线程语义

**事实：** API 2.5.0 Javadoc 没有声明 `MicrophonePacketEvent` 在什么线程触发。

**事实：** 对官方 2.5 系列运行时 `outdated/1.21.6-2.5.30` 的固定提交检查发现：

1. 网络包先进入 `packetQueue`。
2. daemon 线程 `VoiceChatPacketProcessingThread` 轮询队列并调用 `onMicPacket`。
3. `PluginManager#onMicPacket` 同步 `dispatchEvent`。
4. `dispatchEvent` 直接逐个调用已注册 consumer，没有异步切换。

当前 Minecraft 1.21.11 / Simple Voice Chat 2.6.21 固定提交仍采用同样结构。[2.5.30 `Server`](https://github.com/henkelmax/simple-voice-chat/blob/2de36eb3ab9cd9ccc180a60055bdd2d54fdf3fab/common/src/main/java/de/maxhenkel/voicechat/voice/server/Server.java) · [2.5.30 `PluginManager`](https://github.com/henkelmax/simple-voice-chat/blob/2de36eb3ab9cd9ccc180a60055bdd2d54fdf3fab/common/src/main/java/de/maxhenkel/voicechat/plugins/PluginManager.java) · [2.6.21 `Server`](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/common/src/main/java/de/maxhenkel/voicechat/voice/server/Server.java) · [2.6.21 `PluginManager`](https://github.com/henkelmax/simple-voice-chat/blob/277c4dd406b2070e6c551266eda2fec81c090052/common/src/main/java/de/maxhenkel/voicechat/plugins/PluginManager.java)

**推断/建议：** 事件处理必须是非阻塞的热路径：只读取不可变局内快照、复制 UUID/Opus bytes、执行一次 `boundedQueue.offer()`，然后返回。禁止在回调内：

- Opus 解码、重采样、VAD、KWS/ASR；
- 等待锁、future、磁盘或网络；
- 直接读取/修改非线程安全的 Minecraft 世界和实体状态；
- 记录原始音频。

识别结果最终通过 Minecraft server executor 回主线程，并再次核验局 ID、玩家、当前任务/违禁词版本、存活状态和“该次命中是否已处理”，再触发扣血信号。

## 4. 候选方案对比

| 方案 | 中文与离线流式 | 动态有限词表 / KWS | JVM 与 Windows/Linux | 官方体积/内存信息 | 许可与结论 |
|---|---|---|---|---|---|
| **sherpa-onnx KWS** | 官方中英双语流式 KWS 模型 | 原生 open-vocabulary KWS；每个 stream 动态关键词；支持每词 boost/threshold | Java/Kotlin；官方按平台 native JAR，含 Win x64、Linux x64/arm64 | 模型压缩包约 31.4 MiB；chunk-8 160 ms、chunk-16 320 ms。未找到该模型官方 RSS/CPU 数据 | 代码 Apache-2.0；现成模型权重许可需确认。**技术首选，发布有门槛** |
| **Vosk 0.3.45** | 中文小/大模型；真正流式 ASR | 小模型支持运行时 grammar；不是专用 KWS；OOV 有限制 | Java/JNA；Maven JAR 自带 Win/Linux x64 库 | 中文小模型 42 MB；官方称小模型通常约 300 MB 运行内存 | 代码和 `small-cn-0.22` 都标 Apache-2.0。**许可清晰的首版备选** |
| **whisper.cpp** | 多语言模型支持中文；离线 | prompt/grammar 可约束转写，但无专用 KWS 触发分数；官方实时例为滑窗重转写 | 有官方 Java/JNA binding；Win DLL 随旧 Maven JAR，Linux 需外置库；核心与 Java 版本错位 | tiny 约 273 MB RAM，base 约 388 MB，small 约 852 MB；还要滑窗计算 | 核心与 OpenAI 权重 MIT。**不建议首版** |
| **openWakeWord** | 官方明确当前只支持英语 | 每个新词通常要训练模型，不适合每局动态词 | 官方主路径是 Python/ONNX，无桌面 JVM 发行物 | 依赖模型训练与阈值调参 | 代码 Apache-2.0，但随附预训练模型 CC BY-NC-SA 4.0。**不适合中文动态词** |

> 表中大小均来自官方页面/发布物；“约 31.4 MiB”是官方资产 32,885,699 bytes 换算值。未找到官方测量值的项目明确留空，不用参数量推算 RSS 或端到端延迟。

## 5. 候选详查

### 5.1 sherpa-onnx：最匹配游戏规则的 KWS 引擎

#### 已核验事实

- 官方将 KWS 描述为 **open-vocabulary keyword spotting**：无需重新训练即可指定任意关键词；beam-search KWS 只触发给定词，不返回普通转写。[KWS 文档](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- 关键词文件支持每个词独立设置 boosting score 与 trigger threshold；提高阈值可减少误触发，但会增加漏检。[KWS 文档](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- `KeywordSpotter#createStream(String keywords)` 允许每条流在创建时传入自己的关键词。因此可以共享一个模型/spotter，为每位玩家维护一个 stream，并在任务变更时重建该玩家 stream。[Java `KeywordSpotter`](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/KeywordSpotter.java)
- `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` 支持中文和英文；chunk-8 的算法延迟为 160 ms，chunk-16 为 320 ms。官方也说明低延迟配置通常会牺牲一些准确率。[模型页](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html)
- KWS 示例接受单声道 16-bit 音频，并明确说明输入采样率不必是 16 kHz，只需把真实采样率传给 stream；库会处理采样率差异。[模型页](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html)
- Java 非 Android 发行采用一个纯 Java JAR 加一个平台原生 JAR；官方列出 Linux x64/arm64、macOS x64/arm64、Windows x64。[Java 部署文档](https://k2-fsa.github.io/sherpa/onnx/java-api/non-android-java.html)
- 截至调研日，官方最新 release 为 `v1.12.36`；发布资产含纯 Java JAR、Linux x64 native JAR 和 Windows x64 native JAR。[v1.12.36 发布页](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.36)
- sherpa-onnx 代码为 Apache-2.0。[官方 LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE)

#### 工程判断

- **推断/建议：** DDI 的规则只需要判断一个很小的动态集合是否出现，专用 KWS 比“先完整转写、再字符串匹配”更贴合目标，也能直接用 per-keyword threshold 控制“误扣血”风险。
- **推断/建议：** 先以 chunk-8 int8 作为压测起点；如果弱口音/嘈杂环境漏检明显，再比较 chunk-16。160/320 ms 只是模型算法延迟，不等于从玩家说完到服务端扣血的端到端延迟。
- **推断/建议：** 使用官方 `text2token` 工具为中英混合词生成 token；每个任务保存“展示文本 + 识别 token/读音 + aliases”，不要在运行中自行猜拼音。[KWS 文档中的 `text2token`](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- **发布阻塞项：** 官方代码 LICENSE 不能自动证明独立模型发布资产也按 Apache-2.0 授权。本次核验的模型 release 与压缩包没有明确权重许可证。模型可否随模组/服务端包再分发，必须先得到维护者或模型卡的明确许可；在此之前，不应把该模型打进公开发布物。

### 5.2 Vosk：最稳妥的许可清晰备选

#### 已核验事实

- Vosk 官方声明它可离线工作、支持流式 API、20 多种语言（含中文）和 Java，并支持快速重配置词汇。[官方仓库](https://github.com/alphacep/vosk-api)
- 官方模型页列出 `vosk-model-small-cn-0.22`：42 MB、Apache-2.0；大模型 `vosk-model-cn-0.22` 为 1.3 GB。官方说明小模型通常约 50 MB、运行时约 300 MB，并且大多数小模型支持动态词汇；大模型最高可到约 16 GB 内存。[模型页](https://alphacephei.com/vosk/models)
- Java `Recognizer` 可用 JSON phrases 构造或 `setGrammar()` 更新有限语法；官方源码明确说只适用于支持动态图的模型，不适用于预编译 HCLG 图的大模型。grammar 可以包含 `[unk]`。[Java `Recognizer`](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java)
- `acceptWaveForm` 接受 16-bit mono PCM，并返回 partial/final 结果；`Model` 可被多个 recognizer/线程共享，每位玩家仍应有独立 recognizer 状态。[Java `Recognizer`](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java) · [Java `Model`](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Model.java)
- 官方 adaptation 文档说明 online vocabulary modification 依赖可动态修改的解码图。[Adaptation 文档](https://alphacephei.com/vosk/adaptation)
- Maven Central 当前可用的稳定 Java artifact 是 `com.alphacephei:vosk:0.3.45`；其单个 JAR 内包含 `linux-x86-64/libvosk.so`、`win32-x86-64/libvosk.dll` 及相应运行库，使用 JNA 加载。[Maven metadata](https://repo1.maven.org/maven2/com/alphacephei/vosk/maven-metadata.xml) · [0.3.45 JAR](https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar)
- Vosk 代码为 Apache-2.0。[官方 COPYING](https://github.com/alphacep/vosk-api/blob/master/COPYING)

#### 限制与首版用法

- **事实：** 有限 grammar 并不会让模型凭空认识词典外词；模型缺少的词会被忽略或报 OOV 警告。这对 Minecraft 专名、拼音梗、玩家自造词尤其重要。[Recognizer grammar 源码](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java)
- **推断/建议：** 词库加载/管理后台必须先做可识别性校验；OOV 词禁止进入语音任务，或要求策划提供模型内已有的同音/常用别名。长期若要完整覆盖专名，需要重建词典和动态图，而不是只改 JSON grammar。
- **推断/建议：** 每位玩家使用一个 recognizer，grammar 只放当前违禁词、明确 aliases 和 `[unk]`。`[unk]` 很重要，否则极小 grammar 可能把无关语音强行解释成目标词。
- **推断/建议：** 首版只在 final result 或经过稳定性/置信度门槛后触发，不能看到 partial 中出现一次字符串就扣血。中文一字词应默认禁用。
- **事实与风险：** GitHub 项目有比 0.3.45 更新的源码/发布标签，但 Maven Central Java artifact 元数据仍停在 0.3.45。首版应锁定并测试这个实际可获得的 artifact，而不是依赖未发布的 master 版本。[Maven metadata](https://repo1.maven.org/maven2/com/alphacephei/vosk/maven-metadata.xml) · [官方 releases](https://github.com/alphacep/vosk-api/releases)

### 5.3 whisper.cpp：能力强，但不适合首版判罚

#### 已核验事实

- whisper.cpp 为 MIT；OpenAI Whisper 代码和官方模型权重也按 MIT 发布，多语言模型支持中文。[whisper.cpp LICENSE](https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE) · [OpenAI Whisper LICENSE](https://github.com/openai/whisper/blob/main/LICENSE) · [OpenAI Whisper 仓库](https://github.com/openai/whisper)
- 官方内存表：tiny 模型约 75 MiB 磁盘/273 MB 内存，base 约 142 MiB/388 MB，small 约 466 MiB/852 MB；更大模型更高。[Memory usage](https://github.com/ggml-org/whisper.cpp#memory-usage)
- 官方 `whisper-stream` 被明确称为 naive real-time example：默认每 0.5 秒取样，并反复转写一个较长滑动窗口。它不是具备每词 trigger score 的流式 KWS 引擎。[Real-time example](https://github.com/ggml-org/whisper.cpp#real-time-audio-input-example)
- 当前 CLI 支持 initial prompt 和 GBNF grammar，也支持 Silero VAD；grammar 约束的是解码文本，不等于专用 KWS 的误报控制。[CLI options](https://github.com/ggml-org/whisper.cpp/tree/master/examples/cli) · [VAD 文档](https://github.com/ggml-org/whisper.cpp#voice-activity-detection-vad)
- 官方仓库包含 Java/JNA binding，说明测试过 Ubuntu x86_64 和 Windows x86_64。Maven Central 的 `io.github.ggerganov:whispercpp` 只有 2023 年的 `1.4.0`；该 JAR 随带 Windows x64 DLL，但 Linux 需要单独提供共享库。核心项目截至调研日已发布 `v1.9.1` 原生包。[Java binding README](https://github.com/ggml-org/whisper.cpp/blob/master/bindings/java/README.md) · [Maven metadata](https://repo1.maven.org/maven2/io/github/ggerganov/whispercpp/maven-metadata.xml) · [v1.9.1](https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.1)

#### 不作为首版的原因

- **推断：** 对只判定少量词的场景，滑窗完整转写会浪费 CPU，并引入窗口步长 + 推理时长；多人同时说话时更难控制尾延迟。
- **推断：** prompt/grammar 可能提高目标词出现概率，也可能把相近语音“吸”成目标词；缺少专用 KWS threshold 使误扣血更难校准。
- **推断：** 当前核心原生库与旧 Maven Java binding 的版本/ABI 配对没有官方兼容矩阵，跨 Windows/Linux 打包和升级成本高于另外两项。
- **建议：** 如果未来需要保留完整文本用于赛后字幕、复核或更复杂语言规则，可作为独立可选后端；仍不应默认保存音频或文本日志。

### 5.4 其他候选为何不进入首版

#### openWakeWord

- **事实：** 官方 README 明确当前只支持英语；新增 wake word 需要训练专用模型，官方流程依赖大量合成正例与负例，不适合每局动态更换中文词。[官方仓库](https://github.com/dscripka/openWakeWord)
- **事实：** 代码是 Apache-2.0，但仓库随附预训练模型是 CC BY-NC-SA 4.0；官方主路径为 Python/ONNX，没有官方桌面 JVM 发布物。[官方 LICENSE](https://github.com/dscripka/openWakeWord/blob/main/LICENSE) · [README 许可证说明](https://github.com/dscripka/openWakeWord#license)
- **结论：** 不适合中文、动态词和可公开分发的 Minecraft 首版。

#### PocketSphinx

- **事实：** PocketSphinx 是轻量级 C 语音识别器，采用宽松 BSD 风格许可证，当前官方接口重心是 C/Python。[官方仓库](https://github.com/cmusphinx/pocketsphinx) · [官方 LICENSE](https://github.com/cmusphinx/pocketsphinx/blob/master/LICENSE)
- **推断：** 官方当前资料没有提供与 Vosk/sherpa-onnx 同等级的现代中文模型、Java 桌面发行与动态中文 KWS 路径；引入它不会降低首版集成风险。
- **结论：** 不列入首版候选。

## 6. 推荐的完整音频链路

```text
MicrophonePacketEvent（SVC 专用语音包处理线程）
  │ 只取 UUID + 复制 Opus bytes + boundedQueue.offer()
  ▼
按玩家串行的有界队列 / worker pool
  │ 每玩家一个 SVC OpusDecoder，保持到断开/规则结束
  ▼
48 kHz / mono / PCM16（通常每包 20 ms、960 samples）
  │ short → float；必要时只做一次抗混叠 48k→16k 重采样
  ▼
VAD / 静音门控 / endpoint（保留短 pre-roll，超时后 reset stream）
  ▼
sherpa-onnx KWS stream 或 Vosk grammar recognizer
  │ 目标词 + 明确 aliases；阈值/稳定结果；一次任务只命中一次
  ▼
Minecraft server executor
  │ 重验 gameId / playerId / objectiveId / assignmentToken / opt-in / alive
  ▼
触发 DDI 扣血信号与 UI/音效反馈
```

### 6.1 事件入口与背压

- **事实：** SVC 回调同步占用语音包处理线程，见第 3.3 节。
- **建议：** 只有当前被分配到语音违禁词任务、且已同意启用识别的玩家才入队；不要为所有在线玩家持续识别。
- **建议：** 队列必须有界，`offer` 失败时计数并清空该玩家当前 utterance，随后 reset Opus decoder 与 KWS/ASR stream。不要无限堆积，也不要随机丢一个中间 Opus 包后继续解码。
- **建议：** “队列保留约 1 秒音频”可作为首轮压测假设，不是官方结论；最终由并发玩家数、CPU 和允许的处罚延迟确定。
- **建议：** 玩家断开、换任务、局结束和服务停止时，清空队列并关闭 decoder/recognizer/stream。

### 6.2 Opus → PCM

- 使用 `VoicechatApi#createDecoder()`；每位玩家独立 decoder，同一玩家严格按事件到达顺序解码。
- 预期输出为 48 kHz mono PCM16；`short` 转 float 时归一化到约 `[-1, 1]`。
- 不直接依赖 Simple Voice Chat 内部实现类或其 native handle；只调用 API 的 `OpusDecoder`。
- 不能从公开事件精确恢复所有丢包序号；发现本地背压断流时 reset，避免状态污染。

### 6.3 采样率转换

- **sherpa-onnx KWS：** 官方允许传真实输入采样率，因此可先把 48 kHz PCM 直接喂给 stream，让库内部处理；压测后若 CPU 受限，再考虑在共享音频前处理层显式转换到 16 kHz。
- **Vosk：** 创建 recognizer 时传入实际 PCM 采样率；若所选模型/测试结果更适合 16 kHz，则统一做一次 48→16 kHz。
- **whisper.cpp：** Whisper 固定使用 16 kHz 音频，必须转换。[官方 `WHISPER_SAMPLE_RATE`](https://github.com/ggml-org/whisper.cpp/blob/master/include/whisper.h)
- **建议：** 重采样必须使用带低通抗混叠的实现，禁止“每三个样本取一个”。任何后端都只转换一次，避免多次量化与重复 CPU。

### 6.4 VAD、分段与流重置

- **事实：** sherpa-onnx 自身也提供离线 VAD，但流式 KWS 可以连续接收音频，不强制先切成完整句子。[sherpa-onnx VAD](https://k2-fsa.github.io/sherpa/onnx/vad/index.html)
- **事实：** Vosk recognizer 有 endpoint/final result 语义，可先用它完成基础分段。[Java `Recognizer`](https://github.com/alphacep/vosk-api/blob/master/java/lib/src/main/java/org/vosk/Recognizer.java)
- **建议：** 首版优先采用后端自带流式状态/endpoint，只加轻量静音门控以节省 CPU，不要一开始叠加第二套重型 VAD。
- **建议：** 静音门控需保存约 150–250 ms pre-roll，避免切掉中文首音；尾部静音可从约 400–600 ms 开始压测。它们是调参起点，不是事实标准。
- **建议：** 每段设置最长时长（例如 5–10 秒）并在超时/长静音/任务切换时 reset，防止长期状态把跨句音节拼成目标词。
- **建议：** 不能把客户端“语音激活”当服务端 VAD：玩家可能使用按键说话，且客户端的开关/延迟不是 DDI 可依赖的判罚边界。[Simple Voice Chat 客户端配置](https://modrepo.de/minecraft/voicechat/wiki/client_config)

### 6.5 关键词与命中判定

词库记录建议拆成：

```text
displayText:     苦力怕
recognitionText: 苦力怕
aliases:         [苦力帕, creeper]
language:        zh/en/mixed
minimumScore:    每词阈值（仅支持时）
```

- 只加入策划明确认可的简繁体、英文名、常见读音 aliases；不要自动无限扩张同音词。
- 默认禁止单个汉字/单音节任务，或要求二次确认。对扣血机制而言，误报通常比漏报更破坏公平性。
- 不做任意 substring 匹配；例如目标“龙”不能因为识别结果“恐龙”就无条件触发，是否包含匹配应由规则显式决定。
- sherpa-onnx：用 per-keyword threshold/boost 调参；命中后立即 reset 当前 stream，防止拖尾重复命中。
- Vosk：grammar 加 `[unk]`，优先使用 final result；如果使用 partial，必须要求连续稳定多帧并经过置信度/时长门槛。
- 每次任务分配生成不可复用的 `assignmentToken`；异步结果携带该 token。回主线程时 token 不一致就丢弃，避免旧词识别结果伤害新任务。

## 7. 并发、线程与生命周期建议

### 7.1 最小并发模型

- 一个共享模型实例：sherpa `KeywordSpotter` 或 Vosk `Model`。
- 每位激活玩家一个有状态对象：Opus decoder + KWS stream / Vosk recognizer。
- 一个固定大小 worker pool；同一玩家的任务串行，不同玩家可并行。
- 一个轻量 registry 保存 `playerId → session`，会话关闭与 worker 提交需具备幂等性。

**推断/建议：** 不建议“一玩家一线程”，服务器人数增加时线程和 native context 难以控制。也不建议所有玩家共用一个 recognizer/stream，因为状态会串音。

### 7.2 结果去重

一次命中至少携带：

```text
gameId + playerId + objectiveId + assignmentToken + hitSequence
```

主线程应原子地检查“该 assignmentToken 是否已扣血”。即使 ASR 返回重复结果、worker 重试或尾音再次命中，也只能结算一次；是否在若干秒后重新允许同一词处罚，应是玩法规则，不由识别器隐式决定。

### 7.3 故障降级

- native 库加载失败、模型缺失、校验和不符：禁用语音任务池并明确提示管理员，不要让玩家拿到一个永远无法完成/触发的任务。
- worker 饱和或连续队列溢出：当前语音任务进入 fail-open（不扣血）并记录指标；不能因服务端负载随机处罚。
- 不记录原始 PCM/Opus；默认也不记录完整转写。诊断日志只留时间、后端、目标词 ID、分数、命中/丢弃原因和耗时。

## 8. 验收与调参门槛

### 8.1 必测语料

至少按以下维度制作**获得参与者同意**的本地测试集：

- 普通话、常见地区口音；男/女声和不同麦克风；
- Minecraft 环境声、键盘声、多人串音、网络抖动；
- 真正目标词、近音词、目标词作为更长词一部分、完全无关语音；
- 中英混合词、数字、Minecraft 专名；
- 按键说话开头被切、句尾拖长、快速重复目标词。

### 8.2 指标

- 关键词级 precision、recall、false accepts per hour；**不能只报通用 ASR WER**。
- 从目标词说完到命中结果的 p50/p95/p99。
- `MicrophonePacketEvent` 回调耗时、队列深度/溢出数、worker 利用率。
- 8 人和 32 人同时说话时的 CPU、RSS、Minecraft tick p95/p99，以及 Simple Voice Chat 是否仍顺畅。
- Windows x64 与 Linux x64 各跑一次冷启动、native 加载、连续一局和优雅关闭。

**建议目标（产品/工程假设，非官方保证）：**

- 事件回调 p99 < 1 ms；
- 正常负载零队列溢出；
- 目标词说完后 p95 < 750 ms 反馈；
- 上线前用实际词库定义可接受的 false accepts/hour，短词设置更严格阈值或直接禁用。

这些目标需要在目标服务器硬件上校准，不能从模型参数量或官方算法延迟直接推出。

## 9. 隐私、公平性与玩法约束

- 默认只做本机/服务端离线处理，不调用云 API。
- 服务器配置显式启用；玩家加入局前明确提示并同意麦克风内容会被实时机器判定。
- 不保存原始音频，不在日志中保存完整语句；崩溃转储和 debug 模式也应遵守。
- 旁人声音、音响回放、主播视频和玩家本人声音对单麦克风输入不可区分；玩法不能宣称具备说话人验证或防回放能力。
- 识别失效时 fail-open；处罚需有即时反馈，并给管理员可审计的“目标词 ID、分数、后端、时间”，但不暴露玩家完整语句。
- 对单字、粗口近音、高频语气词设禁用名单；这比不断降低阈值更能改善公平性。

## 10. 分阶段推荐

### 阶段 A：不接玩法的离线原型

1. 从录制并获同意的本地 PCM 测试集比较 sherpa-onnx chunk-8/chunk-16 和 Vosk small-cn。
2. 只统计命中，不扣血；建立每词阈值和禁用词规则。
3. 核验 sherpa 官方模型权重、tokens/phone 文件的许可证与再分发权限。

### 阶段 B：影子模式接入 Simple Voice Chat

1. 实现非阻塞事件入队、每玩家 Opus decoder、worker 与 server-thread 回调。
2. 结果只写结构化指标并给管理员测试提示，不影响玩家生命值。
3. 用 8/32 人并发验证语音转发和服务器 tick 不受影响。

### 阶段 C：首版发布

- **若模型许可确认：** `sherpa-onnx v1.12.36` + `zh-en-3M` int8 chunk-8 起步，按词阈值；chunk-16 作为准确率配置项。
- **若许可未确认：** `Vosk Java 0.3.45` + `vosk-model-small-cn-0.22`，有限 grammar + `[unk]` + OOV 预检；模型可由管理员单独下载并校验 SHA-256，但“单独下载”不能替代对使用许可的确认。
- **不选：** whisper.cpp、openWakeWord、PocketSphinx 作为首版扣血判定器。

## 11. 最终建议矩阵

| 决策 | 推荐 |
|---|---|
| 核心检测范式 | 专用流式 KWS，避免先做全文转写 |
| 技术首选 | sherpa-onnx KWS + 中英双语 3M int8 chunk-8 |
| sherpa 发布门槛 | 明确模型权重与词典的许可证/再分发许可 |
| 许可清晰备选 | Vosk 0.3.45 + small-cn-0.22 + 动态 grammar + `[unk]` |
| 后续高配后端 | whisper.cpp，仅用于完整转写/复核，不直接替代 KWS |
| Opus 解码 | Simple Voice Chat 官方 `OpusDecoder`，每玩家独立、串行、及时 close/reset |
| 回调线程 | 只做快照与有界入队；所有重活移出 SVC 线程 |
| 采样率 | sherpa 可先直接接收 48 kHz；需要时统一抗混叠转 16 kHz |
| 误报控制 | 禁单字、显式 aliases、每词阈值、一次 assignment 只结算一次、fail-open |
| 隐私 | 明示同意、全离线、不保存音频/全文、不做说话人身份承诺 |

这套路径把最大的不确定性提前暴露出来：**先解决模型许可和真实词库误报，再接扣血**。单纯证明“能把语音转成文字”不足以证明它适合做自动处罚。
