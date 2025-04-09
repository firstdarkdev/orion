/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.paper

import com.hypherionmc.orion.plugin.paper.OrigamiExtension
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

open class BeforeCompileTask: DefaultTask() {

    @TaskAction
    @Throws(IOException::class)
    fun prepareSourcesTask() {
        val extension = project.extensions.getByType(OrigamiExtension::class.java)
            ?: throw GradleException("Cannot find origami extension on project")

        val common = project.rootProject.findProject(extension.commonProject.get())
            ?: throw GradleException("Cannot find common project ${extension.commonProject.get()}")

        val sourcesFolder = File(common.projectDir, "src/main")

        if (!sourcesFolder.exists()) {
            project.logger.warn("Cannot find sources folder in ${extension.commonProject.get()}")
            return
        }

        project.logger.lifecycle("⚡ Start Processing Plugin Shared Sources")

        val destFolder = File(project.layout.buildDirectory.asFile.get(), "commonShared")
        if (destFolder.exists())
            FileUtils.deleteDirectory(destFolder)

        FileUtils.copyDirectory(sourcesFolder, destFolder)

        project.logger.lifecycle("⚡ Removing Excluded Packages")
        for (excludedPackage in extension.excludedPackages.get()) {
            val pkg = File(destFolder, "java/${excludedPackage.replace('.', '/')}")
            if (pkg.exists())
                FileUtils.deleteDirectory(pkg)
        }
        logger.lifecycle("\uD83E\uDDF9 Successfully processed Excluded Packages")

        project.logger.lifecycle("⚡ Removing Excluded Resources")
        for (excludedResource in extension.excludedResources.get()) {
            val res = File(destFolder, "resources/${excludedResource}")
            if (res.exists()) {
                if (res.isDirectory) {
                    FileUtils.deleteDirectory(res)
                } else {
                    FileUtils.delete(res)
                }
            }
        }
        logger.lifecycle("\uD83E\uDDF9 Successfully processed Excluded Resources")

        processComments(destFolder)

        project.tasks.withType(JavaCompile::class.java).forEach { t -> t.source(File(destFolder, "java")) }
    }

    private fun processComments(sourceDir: File) {
        project.logger.lifecycle("⚡ Running Comment Processor")
        try {
            Files.walkFileTree(sourceDir.toPath(), object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                        stripSpecialCode(file.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            throw GradleException(e.message ?: "Error while reading file", e)
        }
        logger.lifecycle("\uD83E\uDDF9 Successfully processed Comments")
    }

    private fun stripSpecialCode(file: File) {
        try {
            val content = FileUtils.readFileToString(file, StandardCharsets.UTF_8)

            if (content.contains("// @excludeplugin")) {
                FileUtils.delete(file)
                return
            }

            val regex = "(?m)(?s)^\\s*// @noplugin.*?// #noplugin\\s*$"

            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(content)

            var updatedContent = matcher.replaceAll("\n")
            updatedContent = updatedContent.replace("(?m)^[ \t]*\n{2,}".toRegex(), "\n")

            FileUtils.write(file, updatedContent, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            throw GradleException(e.message ?: "Error while writing file", e)
        }
    }

}