/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hypherionmc.orion.meta.ModData
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.NullOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.annotations.Nullable
import org.tomlj.Toml
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*


object FileTools {

    @Throws(Exception::class)
    fun hashFile(file: File): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(FileInputStream(file), digest).use { dis ->
            IOUtils.copy(dis, NullOutputStream.INSTANCE)
        }
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    @Nullable
    fun findRootFolder(file: File, roots: MutableList<File>): File? {
        val filePath = file.getCanonicalPath()
        for (root in roots) {
            val rootPath = root.getCanonicalPath()
            if (filePath.startsWith(rootPath + File.separator)) {
                return root
            }
        }

        return null
    }

    fun cleanupEmptyParents(file: File, root: File?) {
        var parent = file.getParentFile()
        try {
            while (parent != null && parent != root) {
                val list = parent.listFiles()
                if (list == null) break

                if (!parent.delete()) break
                parent = parent.getParentFile()
            }
        } catch (e: java.lang.Exception) {
            System.err.println("Failed to clean up: " + e.message)
        }
    }

    fun resolveFile(project: Project, objj: Any?): File {
        var obj = objj
        if (obj == null) {
            throw NullPointerException("Null Path")
        }

        obj = project.tasks.getByName(obj.toString()) as Any

        if (obj is Provider<*>) {
            val p: Provider<*> = obj
            obj = p.get()
        }

        if (obj is File) {
            return obj
        }

        if (obj is AbstractArchiveTask) {
            return obj.archiveFile.get().asFile
        }

        return project.file(obj)
    }

    fun resolveInputTask(project: Project?, inTask: Any?, mainTask: Task?) {
        if (project == null || inTask == null || mainTask == null) return

        var task: Task? = null

        if (inTask is Provider<*>) {
            val p = inTask
            task = p.get() as Task
        }

        if (inTask is String) {
            task = project.tasks.getByName(inTask)
        }

        if (inTask is Task) {
            task = inTask
        }

        if (task !is AbstractArchiveTask) return

        mainTask.dependsOn(task)
    }

    fun readModMetadata(paths: List<File>): ModData {
        val metadataFiles = listOf("fabric.mod.json", "quilt.mod.json", "mods.toml", "neoforge.mods.toml")
        var meta = ""

        var isToml = false
        for (path in paths) {
            for (metaPath in metadataFiles) {
                val f = File(path, metaPath)
                if (!f.exists() || !f.isFile) continue
                meta = FileUtils.readFileToString(f, StandardCharsets.UTF_8)
                isToml = f.extension == "toml"
                break
            }
        }

        if (meta.isEmpty()) {
            throw GradleException("Failed to read mod metadata. No valid mod entry found")
        }

        if (isToml) {
            val toml = Toml.parse(meta)
            val tb = toml.getArray("mods")?.getTable(0) ?: throw GradleException("Failed to read mod metadata. No valid mod entry found")

            return ModData(
                tb.getString("modId") { "unknown" },
                tb.getString("displayName") { "unknown" },
                tb.getString("version") { "unknown" },
                false,
                toml.getString("license") { "unknown" },
            )
        }

        val metadata = Gson().fromJson(meta, JsonObject::class.java)
        return ModData(
            metadata.get("id").asString,
            metadata.get("name").asString,
            metadata.get("version").asString,
            true,
            metadata.get("license").asString,
        )
    }

    @Throws(IOException::class)
    fun download(coordinate: String, outputDir: File) {
        // Parse coordinate: group:artifact:version[:classifier]
        val parts = coordinate.split(":")
        require(parts.size in 3..4) { "Coordinate must be in format group:artifact:version[:classifier]" }

        val groupId = parts[0]
        val artifactId = parts[1]
        val version = parts[2]
        val classifier = if (parts.size == 4) parts[3] else null

        val baseUrl = "https://maven.firstdark.dev/releases"
        val jarName = buildString {
            append(artifactId).append("-").append(version)
            if (classifier != null) append("-").append(classifier)
            append(".jar")
        }

        val path = "${groupId.replace('.', '/')}/$artifactId/$version/$jarName"
        val url = URL("$baseUrl/$path")

        println("Downloading: $url")
        url.openStream().use { input ->
            Files.copy(input, outputDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Downloaded to: ${outputDir.toPath().toAbsolutePath()}")
        }
    }
}