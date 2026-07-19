package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoiceKeywordModelArchiveTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `extracts only the expected model tree`() {
        val archive = temp.resolve("model.zip")
        writeZip(archive, mapOf("model/conf/model.conf" to "ok"))
        val destination = temp.resolve("extract")
        val progress = mutableListOf<Long>()

        VoiceKeywordModelArchive.extract(
            archive,
            destination,
            "model",
            10,
            1_024,
            onProgress = progress::add,
        )

        assertThat(Files.readString(destination.resolve("model/conf/model.conf")))
            .isEqualTo("ok")
        assertThat(progress.last()).isEqualTo(2L)
    }

    @Test
    fun `rejects zip slip and unexpected archive roots`() {
        val traversal = temp.resolve("traversal.zip")
        writeZip(traversal, mapOf("model/../../outside" to "bad"))
        assertThrows<IOException> {
            VoiceKeywordModelArchive.extract(
                traversal,
                temp.resolve("extract-traversal"),
                "model",
                10,
                1_024,
            )
        }

        val wrongRoot = temp.resolve("wrong.zip")
        writeZip(wrongRoot, mapOf("other/file" to "bad"))
        assertThrows<IOException> {
            VoiceKeywordModelArchive.extract(
                wrongRoot,
                temp.resolve("extract-wrong"),
                "model",
                10,
                1_024,
            )
        }
    }

    @Test
    fun `model preparation percentage is only shown for known totals`() {
        assertThat(
            VoiceKeywordModelProgress(
                phase = VoiceKeywordModelProgressPhase.DOWNLOADING,
                completedBytes = 2L,
                totalBytes = 4L,
            ).percent,
        ).isEqualTo(50)
        assertThat(
            VoiceKeywordModelProgress(
                phase = VoiceKeywordModelProgressPhase.EXTRACTING,
                completedBytes = 2L,
            ).percent,
        ).isNull()
    }

    private fun writeZip(path: Path, entries: Map<String, String>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
