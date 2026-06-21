package com.hypherionmc.orion.utils.unimined

import com.google.gson.JsonParser
import com.hypherionmc.orion.Constants.ORION_VERSION
import io.sigpipe.jbsdiff.Patch
import org.gradle.api.GradleException
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.api.minecraft.patch.bukkit.PaperPatcher
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.util.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.StandardOpenOption
import kotlin.collections.first
import kotlin.collections.plus
import kotlin.collections.setOf
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

open class PaperMCTransformer(project: Project,
                                     provider: MinecraftProvider
) : CBMinecraftTransformer(project, provider, "paper"), PaperPatcher {

    override val supportedEnvs = setOf(EnvType.SERVER)

    override var build: Int by MustSet()

    override var paper: File by FinalizeOnRead(LazyMutable {
        project.cachingDownload(
            URI.create(getPaperDownloadUrl()),
            cachePath = project.unimined.getGlobalCache().resolve("paper/${provider.version}/paper-${provider.version}-${build}.jar")
        ).toFile()
    })

    override var patchName by FinalizeOnRead(Regex("META-INF/.*/server-.*\\.patch"))

    init {
        loader = provider.version
    }

    override fun loadLibraries() {
        // TODO
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val output = MinecraftJar(
            minecraft,
            patches = minecraft.patches + "paper-$loader",
            mappingNamespace = provider.mappings.checkedNs(if (provider.obfuscated) "spigotProd" else "official"),
        )

        val content = paper.toPath().readZipContents()
        // find patch
        val patch = content.first {
            it == "paperMC.patch" || it.matches(patchName)
        }

        output.path.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { os ->
            paper.toPath().readZipInputStreamFor(patch) {
                Patch.patch(minecraft.path.readBytes(), it.readBytes(), os)
            }
        }

        addExtraInnerClassMappings(minecraft, output)

        return output
    }

    fun getPaperDownloadUrl(): String {
        try {
            val versionUrl = URI.create("https://fill.papermc.io/v3/projects/paper/versions/${provider.version}/builds/${build}").toURL()
            val connection = versionUrl.openConnection() as HttpURLConnection
            connection.readTimeout = 5000
            connection.connectTimeout = 5000
            connection.setRequestProperty("User-Agent", "Orion/${ORION_VERSION} (https://github.com/firstdarkdev/orion")
            connection.setRequestProperty("Accept", "application/json")

            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                val downloads = json.getAsJsonObject("downloads")
                val serverDefault = downloads.getAsJsonObject("server:default")

                return serverDefault.get("url").asString
            }
        } catch (e: Exception) {
            throw GradleException("Failed to fetch paper build $build", e)
        }
    }

}