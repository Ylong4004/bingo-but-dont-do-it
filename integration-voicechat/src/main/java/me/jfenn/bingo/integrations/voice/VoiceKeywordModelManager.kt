package me.jfenn.bingo.integrations.voice

import org.vosk.LibVosk
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.Comparator
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

internal class VoiceKeywordModelManager(
    private val root: Path,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    companion object {
        const val MODEL_DIRECTORY_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_ZIP_LENGTH = 43_898_754L
        const val MODEL_ZIP_SHA256 =
            "3AF8B0E7E0F835AE9D414CE5DF580237A3CFB08D586C9FBBB0F7FF29AD5B14BA"
        val MODEL_URI: URI = URI.create(
            "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        )

        private const val MAX_ARCHIVE_ENTRIES = 512
        private const val MAX_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
        private val REQUIRED_MODEL_FILES = listOf(
            "am/final.mdl",
            "conf/mfcc.conf",
            "conf/model.conf",
            "graph/HCLr.fst",
            "graph/Gr.fst",
        )
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bingo-voice-model").apply { isDaemon = true }
    }
    private val state = AtomicReference(initialStatus())
    private val loadRequested = AtomicBoolean(false)
    private val operationLock = Any()

    @Volatile
    private var model: Model? = null
    private var downloadFuture: CompletableFuture<VoiceKeywordBackendStatus>? = null
    private var loadFuture: CompletableFuture<VoiceKeywordBackendStatus>? = null

    fun status(): VoiceKeywordBackendStatus = state.get()

    fun loadedModel(): Model? = model

    fun ensureLoadedAsync(): CompletableFuture<VoiceKeywordBackendStatus> {
        loadRequested.set(true)
        model?.let {
            return CompletableFuture.completedFuture(update(VoiceKeywordBackendState.READY))
        }
        synchronized(operationLock) {
            loadFuture?.takeIf { !it.isDone }?.let { return it }
            val future = CompletableFuture.supplyAsync({ loadModelNow() }, executor)
            loadFuture = future
            future.whenComplete { _, _ ->
                synchronized(operationLock) {
                    if (loadFuture === future) loadFuture = null
                }
            }
            return future
        }
    }

    fun requestDownload(): CompletableFuture<VoiceKeywordBackendStatus> {
        synchronized(operationLock) {
            downloadFuture?.takeIf { !it.isDone }?.let { return it }
            val future = CompletableFuture.supplyAsync({ downloadAndInstall() }, executor)
            downloadFuture = future
            future.whenComplete { _, _ ->
                synchronized(operationLock) {
                    if (downloadFuture === future) downloadFuture = null
                }
            }
            return future
        }
    }

    private fun initialStatus(): VoiceKeywordBackendStatus {
        if (!isSupportedPlatform()) {
            return modelStatus(
                VoiceKeywordBackendState.UNSUPPORTED_PLATFORM,
                "unsupported_native_platform",
            )
        }
        return when {
            !Files.exists(modelDirectory()) -> modelStatus(VoiceKeywordBackendState.MODEL_MISSING)
            isValidModelDirectory(modelDirectory()) ->
                modelStatus(VoiceKeywordBackendState.MODEL_AVAILABLE)
            else -> modelStatus(VoiceKeywordBackendState.MODEL_INVALID, "invalid_model_layout")
        }
    }

    private fun loadModelNow(): VoiceKeywordBackendStatus {
        model?.let { return update(VoiceKeywordBackendState.READY) }
        if (!isSupportedPlatform()) {
            return update(
                VoiceKeywordBackendState.UNSUPPORTED_PLATFORM,
                "unsupported_native_platform",
            )
        }
        val directory = modelDirectory()
        if (!Files.exists(directory)) return update(VoiceKeywordBackendState.MODEL_MISSING)
        if (!isValidModelDirectory(directory)) {
            return update(VoiceKeywordBackendState.MODEL_INVALID, "invalid_model_layout")
        }

        update(VoiceKeywordBackendState.LOADING)
        return try {
            // 识别器输出可能包含当前 Vosk 语法；原生日志只保留警告级别，且本模块绝不
            // 主动记录识别结果。
            LibVosk.vosk_set_log_level(-1)
            model = Model(directory.toString())
            update(VoiceKeywordBackendState.READY)
        } catch (error: Throwable) {
            model = null
            update(
                VoiceKeywordBackendState.ERROR,
                "native_or_model_load_failed:${error.javaClass.simpleName}",
            )
        }
    }

    private fun downloadAndInstall(): VoiceKeywordBackendStatus {
        if (!isSupportedPlatform()) {
            return update(
                VoiceKeywordBackendState.UNSUPPORTED_PLATFORM,
                "unsupported_native_platform",
            )
        }
        if (isValidModelDirectory(modelDirectory())) {
            update(VoiceKeywordBackendState.MODEL_AVAILABLE)
            return if (loadRequested.get()) loadModelNow() else status()
        }

        update(VoiceKeywordBackendState.DOWNLOADING)
        val archive = root.resolve(".$MODEL_DIRECTORY_NAME-${UUID.randomUUID()}.zip.part")
        val staging = root.resolve(".extract-${UUID.randomUUID()}")
        return try {
            Files.createDirectories(root)
            downloadVerifiedArchive(archive)
            VoiceKeywordModelArchive.extract(
                archive = archive,
                stagingRoot = staging,
                expectedTopDirectory = MODEL_DIRECTORY_NAME,
                maxEntries = MAX_ARCHIVE_ENTRIES,
                maxUncompressedBytes = MAX_UNCOMPRESSED_BYTES,
            )
            val extracted = staging.resolve(MODEL_DIRECTORY_NAME)
            if (!isValidModelDirectory(extracted)) {
                throw IOException("Downloaded model has an invalid layout")
            }
            installAtomically(extracted)
            update(VoiceKeywordBackendState.MODEL_AVAILABLE)
            if (loadRequested.get()) loadModelNow() else status()
        } catch (error: Throwable) {
            update(
                VoiceKeywordBackendState.ERROR,
                "model_download_failed:${error.javaClass.simpleName}",
            )
        } finally {
            Files.deleteIfExists(archive)
            deleteRecursively(staging)
        }
    }

    private fun downloadVerifiedArchive(target: Path) {
        val request = HttpRequest.newBuilder(MODEL_URI)
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "bingo-ddi-voice-model/1")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("Unexpected model response status")
        }
        response.headers().firstValueAsLong("Content-Length").ifPresent { length ->
            if (length != MODEL_ZIP_LENGTH) {
                response.body().close()
                throw IOException("Unexpected model response length")
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        DigestInputStream(BufferedInputStream(response.body()), digest).use { input ->
            Files.newOutputStream(
                target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > MODEL_ZIP_LENGTH) {
                        throw IOException("Model archive exceeds expected length")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        if (copied != MODEL_ZIP_LENGTH) throw IOException("Incomplete model archive")
        val actualHash = HexFormat.of().withUpperCase().formatHex(digest.digest())
        if (actualHash != MODEL_ZIP_SHA256) throw IOException("Model checksum mismatch")
    }

    private fun installAtomically(extracted: Path) {
        val target = modelDirectory()
        val backup = root.resolve(".$MODEL_DIRECTORY_NAME-invalid-${UUID.randomUUID()}")
        var hasBackup = false
        if (Files.exists(target)) {
            moveAtomically(target, backup)
            hasBackup = true
        }
        try {
            moveAtomically(extracted, target)
        } catch (error: Throwable) {
            if (hasBackup && !Files.exists(target) && Files.exists(backup)) {
                moveAtomically(backup, target)
            }
            throw error
        }
        if (hasBackup) runCatching { deleteRecursively(backup) }
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun modelDirectory(): Path = root.resolve(MODEL_DIRECTORY_NAME)

    private fun isValidModelDirectory(directory: Path): Boolean =
        Files.isDirectory(directory) && REQUIRED_MODEL_FILES.all { relative ->
            val file = directory.resolve(relative)
            Files.isRegularFile(file) && runCatching { Files.size(file) > 0L }.getOrDefault(false)
        }

    private fun isSupportedPlatform(): Boolean {
        val arch = System.getProperty("os.arch", "").lowercase()
        if (arch != "amd64" && arch != "x86_64" && arch != "x64") return false
        val os = System.getProperty("os.name", "").lowercase()
        return os.contains("windows") || os.contains("linux") || os.contains("mac")
    }

    private fun update(
        newState: VoiceKeywordBackendState,
        detail: String? = null,
    ): VoiceKeywordBackendStatus = modelStatus(newState, detail).also(state::set)

    private fun modelStatus(
        newState: VoiceKeywordBackendState,
        detail: String? = null,
    ) = VoiceKeywordBackendStatus(
        state = newState,
        voiceChatAvailable = false,
        detail = detail,
    )

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedPath = path.toAbsolutePath().normalize()
        if (normalizedPath == normalizedRoot || !normalizedPath.startsWith(normalizedRoot)) return
        Files.walk(normalizedPath).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}

/** 独立实现可防 Zip Slip 的模型解压逻辑，便于进行针对性测试。 */
internal object VoiceKeywordModelArchive {
    fun extract(
        archive: Path,
        stagingRoot: Path,
        expectedTopDirectory: String,
        maxEntries: Int,
        maxUncompressedBytes: Long,
    ) {
        Files.createDirectory(stagingRoot)
        val normalizedRoot = stagingRoot.toAbsolutePath().normalize()
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(Files.newInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                if (entries > maxEntries) throw IOException("Model archive has too many entries")
                val name = entry.name
                if (name.contains('\\') || name.indexOf('\u0000') >= 0) {
                    throw IOException("Invalid model archive entry")
                }
                if (name != expectedTopDirectory &&
                    !name.startsWith("$expectedTopDirectory/")) {
                    throw IOException("Unexpected model archive root")
                }
                val output = normalizedRoot.resolve(name).normalize()
                if (!output.startsWith(normalizedRoot)) {
                    throw IOException("Model archive entry escapes destination")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                } else {
                    Files.createDirectories(output.parent)
                    Files.newOutputStream(
                        output,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                    ).use { file ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > maxUncompressedBytes) {
                                throw IOException("Model archive is too large")
                            }
                            file.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
