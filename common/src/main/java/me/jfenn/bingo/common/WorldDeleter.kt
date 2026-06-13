package me.jfenn.bingo.common

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.datapack.ServerProps
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.scope.BingoKoin
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.time.measureTime

/**
 * This deletes all world files that should reset when the
 * server restarts. (leaving behind configs, datapacks, & player statistics)
 */
object WorldDeleter {

    private val log = LoggerFactory.getLogger("bingo")

    private fun deleteMatching(cwd: Path, path: String) {
        val part = path.substringBefore("/")
        val after = if (path.length > part.length + 1) {
            path.substring(part.length + 1)
        } else ""

        val matches = cwd.listDirectoryEntries(part)
        for (match in matches) {
            if (after.isEmpty()) {
                log.debug("Deleting file {}", match)
                match.toFile().deleteRecursively()
            } else {
                deleteMatching(match, after)
            }
        }
    }

    fun invoke(server: MinecraftServer) {
        val scope = BingoKoin.getScope(server)
        if (scope == null) {
            log.error("Skipping erroneous WorldDeleter call as Yet Another Bingo is not initialized")
            return
        }

        val environment = scope.get<IModEnvironment>()
        val config = scope.get<BingoConfig>()
        val serverProps = scope.get<ServerProps>()
        val gameDir = environment.gameDir.resolve(serverProps.levelName)
        log.info("Deleting world files in dir: ${gameDir.toAbsolutePath()}")

        measureTime {
            for (fileName in config.server.filesToReset) {
                deleteMatching(gameDir, fileName)
            }
        }.also {
            log.info("Done ($it)")
        }
    }
}
