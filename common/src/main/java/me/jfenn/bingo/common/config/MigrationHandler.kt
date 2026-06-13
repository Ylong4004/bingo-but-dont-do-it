package me.jfenn.bingo.common.config

import me.jfenn.bingo.common.MOD_ID
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.data.TagLoader
import me.jfenn.bingo.common.data.TierListLoader
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.config.IConfigManager
import org.slf4j.Logger
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.*

class MigrationHandler(
    private val log: Logger,
    private val config: BingoConfig,
    private val environment: IModEnvironment,
    private val configManager: IConfigManager,
) {

    private val migrations = mutableMapOf<Int, (BingoConfig) -> Unit>()

    init {
        migrations[1] = {
            if (environment.envType == IModEnvironment.EnvType.SERVER) {
                environment.gameDir.resolve("world/datapacks/bingo-lobby.zip")
                    .takeIfMd5("89c7644b5cbcf785f25839364ad61788")
                    ?.deleteIfExists()

                environment.gameDir.resolve("world/datapacks/bingo-lobby.zip")
                    .takeIfMd5("df4016673876474b77cac543ebb3f7f7")
                    ?.deleteIfExists()
            }

            environment.configDir.resolve("bingo/players.json")
                .deleteIfExists()

            environment.configDir.resolve("bingo/overworld.tierlist.json")
                .takeIfMd5("16408324182d09a1a3a80317c0c2269b")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/overworld.tierlist.json")
                .takeIfMd5("45ca6dac60d9cf5232a3c7b21610bc99")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/overworld.tierlist.json")
                .takeIfMd5("9fbe1eb9ed3e4744ec7272e13cb7e7fd")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/nether.tierlist.json")
                .takeIfMd5("7eb68b14b0288982b86a3c6b856482e3")
                ?.deleteIfExists()
        }

        migrations[2] = {
            environment.configDir.resolve("bingo/all_items.tierlist.json")
                .takeIfMd5("37946b44952cab04cd2dde840da5beac")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/overworld-only.tierlist.json")
                .takeIfMd5("747b65ffd7b923cc4e2f746dffacd4e0")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/simplified.tierlist.json")
                .takeIfMd5("707151ae1614dc508ad264677be397e2")
                ?.deleteIfExists()
        }

        migrations[3] = {}

        migrations[4] = {
            environment.configDir.resolve("bingo/all_items.tierlist.json")
                .takeIfMd5("85984ea87c88d93cc9069d0cef529338")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/overworld-only.tierlist.json")
                .takeIfMd5("08e90ebc0fc941be90cdfd3f57035983")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/simplified.tierlist.json")
                .takeIfMd5("1c256401902baeb115b9a7d1547fc417")
                ?.deleteIfExists()

            environment.configDir.resolve("bingo/advancements.tierlist.json")
                .takeIfMd5("000ba600d573dc29e3c12be51ce52ea9")
                ?.deleteIfExists()
        }

        migrations[5] = {
            // move the "config/bingo" dir to "config/yet-another-minecraft-bingo"
            val originalDir = environment.configDir.resolve(MOD_ID)
            val newDir = environment.configDir.resolve(MOD_ID_BINGO)
            if (originalDir.resolve("game-options.json").toFile().exists()) {
                log.info("[MigrationHandler] Renaming config/$MOD_ID -> config/$MOD_ID_BINGO")
                newDir.toFile().deleteRecursively()
                Files.move(originalDir, newDir, StandardCopyOption.REPLACE_EXISTING)
            } else {
                log.warn("[MigrationHandler] Not renaming config/$MOD_ID as it does not exist.")
            }
        }

        migrations[6] = {
            environment.configDir.resolve("$MOD_ID_BINGO/all_items.tierlist.json")
                .takeIfMd5("4e1fbf43d0995bc3b4c85b985c9b6c1f")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/simplified.tierlist.json")
                .takeIfMd5("0135d16de992bdfea1d7c79d6c5b7054")
                ?.deleteIfExists()
        }

        migrations[7] = { // 2.1.0-alpha.0
            environment.configDir.resolve("$MOD_ID_BINGO/nether-only.tierlist.json")
                .takeIfMd5("7eb68b14b0288982b86a3c6b856482e3")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/overworld-only.tierlist.json")
                .takeIfMd5("7f79d26e384946de62c7d9d4f54105e2")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/advancements.tierlist.json")
                .takeIfMd5("19ea980bff9cd124ece633189d2257a2")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/all_items.tierlist.json")
                .takeIfMd5("cfb3aeebd054d727e52b4398aa6c0f8b")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/simplified.tierlist.json")
                .takeIfMd5("0d4f8a30e26cfcbc44a9096d6eef556e", "d4f8a30e26cfcbc44a9096d6eef556e")
                ?.deleteIfExists()
        }

        migrations[11] = { // 2.1.0-alpha.3
            environment.configDir.resolve("$MOD_ID_BINGO/advancements.tierlist.json")
                .takeIfMd5("99c0b10329da4ff1c5224d9f86a3cb2a")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/challenge.tierlist.json")
                .takeIfMd5("dd036cdc11bb671f16ce48fdef978d7b", "7a5449d0c4d6812200c0762090666794")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/all_items.tierlist.json")
                .takeIfMd5("b20e4626cbf9115ac6170cc39f2a109a")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/simplified.tierlist.json")
                .takeIfMd5("2ea9f498a9f5203392d1eb8244136c6f")
                ?.deleteIfExists()
        }

        migrations[16] = { // 2.2.1
            environment.configDir.resolve("$MOD_ID_BINGO/all_items.tierlist.json")
                .takeIfMd5("8af987dce4f22bdd948dab0be442491e", "80db1d320d67b16046eff5d16a3b911a", "7bb179acf2c50992091a50eda4e115b2")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/simplified.tierlist.json")
                .takeIfMd5("8e53697667ad30847642932edfd302d7", "704f1e91c43487a4495bb1d7ac51eeb1")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/challenge.tierlist.json")
                .takeIfMd5("9c0c3347056b79c6dafef1dcb2316f69", "762fb665525a9a7408e460184f1e5803", "980848e7fc9bdc54030a449a65548997", "3509f527976c42b4f72b5d9f5ae57bac")
                ?.deleteIfExists()
        }

        migrations[18] = { // 2.2.5
            environment.configDir.resolve("$MOD_ID_BINGO/challenge.tierlist.json")
                .takeIfMd5("3509f527976c42b4f72b5d9f5ae57bac", "efe9f31c33a51e893c4aff44ffb8f51e")
                ?.deleteIfExists()
        }

        migrations[22] = { // 2.3.0
            environment.configDir.resolve("$MOD_ID_BINGO/all_items.tierlist.json")
                .takeIfMd5("73eddb865b11831a711b9c58753f2bfb")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/advancements.tierlist.json")
                .takeIfMd5("30a50846828cd3e432ff67a5ded29c79", "4b95c5d16e8ee6f5354ddaa872c520c5")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/challenge.tierlist.json")
                .takeIfMd5("79a1242373e76d5bafd9a8843f8130a1", "f13fe7bf41603f2d4760fbc64d00eca8")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/items.tierlist.json")
                .takeIfMd5("9336c61e3bc04ab0d5551558f6728c1c")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/simplified.tierlist.json")
                .takeIfMd5("f692573a7c0437c3f7bb8e35e034a873")
                ?.deleteIfExists()
        }

        migrations[26] = { // 2.4.0
            environment.configDir.resolve("$MOD_ID_BINGO/advancements.tierlist.json")
                .takeIfMd5("155c3c0f1e1133b507b4fe157d6fd83a", "e01cc90047e633d0a5258850735bed13", "b4dccf937cbd7b203e5c965294dea8e4")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/items.tierlist.json")
                .takeIfMd5("1c8c6b3108fba4c289342b472f7442a5")
                ?.deleteIfExists()

            environment.configDir.resolve("$MOD_ID_BINGO/challenge.tierlist.json")
                .takeIfMd5("a2a51e6e8a965ac98b7551ddcb1a8e1")
                ?.deleteIfExists()
        }

        migrations[32] = { // 2.5.0
            // Move *.tierlist.json files from "{config}/" to "{config}/tierlists/"
            val targetPath = environment.configDir.resolve(TierListLoader.TIERLISTS_PATH)
            targetPath.toFile().mkdirs()

            environment.configDir.resolve(MOD_ID_BINGO)
                .toFile()
                .listFiles()
                .orEmpty()
                .filter { it.name.endsWith(TierListLoader.FILE_SUFFIX) }
                .forEach { sourceFile ->
                    Files.move(sourceFile.toPath(), targetPath.resolve(sourceFile.name), StandardCopyOption.REPLACE_EXISTING)
                }

            if (config.databaseUrl == null) {
                environment.configDir.resolve("$MOD_ID_BINGO/stats.db")
                    .takeIf { it.toFile().exists() }
                    ?.let {
                        Files.move(it, environment.configDir.resolve("$MOD_ID_BINGO/bingo.db"), StandardCopyOption.REPLACE_EXISTING)
                    }
            }

            environment.configDir.resolve("$MOD_ID_BINGO/commands/on_loading.mcfunction")
                .takeIf { it.toFile().exists() }
                ?.let {
                    Files.move(it, environment.configDir.resolve("$MOD_ID_BINGO/commands/on_starting.mcfunction"), StandardCopyOption.REPLACE_EXISTING)
                }

            environment.configDir.resolve("$MOD_ID_BINGO/player-settings.json")
                .deleteIfExists()

            val hashes = listOf(
                "tierlists/advancements.tierlist.json" to "5444f9ee52d211499fe75d74fdc89542",
                "tierlists/challenge.tierlist.json" to "124a93320a483b3020e87ffd86f71d65",
                "tierlists/items.tierlist.json" to "9bbc463585e52123d15cd1d72e921551",
                "tierlists/simplified.tierlist.json" to "90fb73f823703ae3d5c8425f949a40f3",
                "tags/biome_specific.json" to "4962ad50a7c7856193abb68980da6543",
                "tags/in_overworld.json" to "29b02bffb6d6dd45b373190c58962ea",
                "tags/in_the_end.json" to "b9909612268e0c79c081530a02d4db28",
                "tags/in_the_nether.json" to "c9baeebe66f08ed75dba91411b0bd128",
                "tags/tedious.json" to "a7809157fbb7dccad86d446846988c9f",
                "tags/unobtainable.json" to "1179f48cbee870f0f964a3c564565900"
            )

            for ((path, hash) in hashes) {
                environment.configDir.resolve("$MOD_ID_BINGO/$path")
                    .takeIfMd5(hash)
                    ?.deleteIfExists()
            }
        }
    }

    private fun md5Of(path: Path): String? {
        val md = MessageDigest.getInstance("MD5")
        val digest = try {
            md.digest(Files.readAllBytes(path))
        } catch (e: IOException) {
            log.error("[MigrationHandler] Error reading $path")
            return null
        }
        val hexDigest = BigInteger(1, digest).toString(16)
        return hexDigest
    }

    private fun Path.takeIfMd5(vararg md5Str: String): Path? {
        if (!this.isRegularFile()) return null

        val hexDigest = md5Of(this) ?: return null
        val matches = md5Str.contains(hexDigest)
        if (matches) {
            log.info("MATCH $hexDigest -- $pathString")
        } else {
            log.info("FAIL $hexDigest -- $pathString")
        }

        return takeIf { matches }
    }

    fun runMigrations() {
        val fromVersion = config.version
        val maxVersion = migrations.keys.max()

        for (i in fromVersion+1..maxVersion) {
            log.info("$MOD_ID: running migrations to v$i")
            migrations[i]?.invoke(config)
        }

        val configService = ConfigService(configManager)
        configService.config = configService.config.copy(version = maxVersion)
        configService.options = configService.options.copy()
        configService.optionsDefault = configService.optionsDefault.copy()

        if (log.isDebugEnabled) {
            listOf(TierListLoader.TIERLISTS_PATH, TagLoader.TAGS_PATH)
                .flatMap {
                    environment.configDir.resolve(TierListLoader.TIERLISTS_PATH)
                        .takeIf { it.isDirectory() }
                        ?.listDirectoryEntries(glob = "*.json")
                        .orEmpty()
                }
                .forEach {
                    log.info("| $MOD_ID_BINGO | ${it.fileName.toString().padEnd(40)} | ${md5Of(it)} |")
                }
        }
    }

}