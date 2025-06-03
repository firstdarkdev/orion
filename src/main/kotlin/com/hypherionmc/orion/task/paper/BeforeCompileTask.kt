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
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import kotlin.collections.listOf

/**
 * @author HypherionSA
 *
 * Gradle task to strip out code and resources, for Paper Plugins compiled against modded code
 * This runs before the resources and source code are processed
 */
open class BeforeCompileTask: DefaultTask() {

    @TaskAction
    @Throws(IOException::class)
    fun prepareSourcesTask() {
        // Check that the extension is registered
        val extension = project.extensions.getByType(OrigamiExtension::class.java)
            ?: throw GradleException("Cannot find origami extension on project")

        // Check that the COMMON (or shared project) is configured, and accessible
        val common = project.rootProject.findProject(extension.commonProject.get())
            ?: throw GradleException("Cannot find common project ${extension.commonProject.get()}")

        val sourcesFolder = File(common.projectDir, "src/main")
        val altSources = common.layout.buildDirectory.dir("generated/wrapped-sources").get().asFile

        // Check that the shared source code folder exists
        if (!sourcesFolder.exists()) {
            project.logger.warn("Cannot find sources folder in ${extension.commonProject.get()}")
            return
        }

        project.logger.lifecycle("⚡ Start Processing Plugin Shared Sources")

        // Set up the temporary processing folder
        val destFolder = File(project.layout.buildDirectory.asFile.get(), "commonShared")
        if (destFolder.exists())
            FileUtils.deleteDirectory(destFolder)

        FileUtils.copyDirectory(sourcesFolder, destFolder)

        if (altSources.exists()) {
            FileUtils.deleteDirectory(File(destFolder, "java"))
            FileUtils.copyDirectory(altSources, destFolder)
        }

        val removedFiles = mutableSetOf<String>()

        // Filter out excluded code packages
        project.logger.lifecycle("⚡ Removing Excluded Packages")
        for (excludedPackage in extension.excludedPackages.get()) {
            val pkg = File(destFolder, "java/${excludedPackage.replace('.', '/')}")
            if (pkg.exists()) {
                FileUtils.deleteDirectory(pkg)
                removedFiles.add(pkg.absolutePath)
            }
        }
        logger.lifecycle("\uD83E\uDDF9 Successfully processed Excluded Packages")

        // Filter out excluded resources
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

        val sources = mutableListOf<Any>()
        sources.add(project.layout.buildDirectory.dir("generated/wrapped-sources"))
        sources.add(File(destFolder, "java"))

        // Give the processed sources and resources back to the compile task
        project.tasks.withType(JavaCompile::class.java).forEach { t -> t.setSource(sources) }
    }

    /**
     * Process code comments that handle excluding certain pieces of code, or even entire classes
     *
     * @param sourceDir The Directory that is being processed
     */
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

    /**
     * Strip code and comments from Source Code
     *
     * @param file The File that is being processed
     */
    private fun stripSpecialCode(file: File) {
        try {
            val content = FileUtils.readFileToString(file, StandardCharsets.UTF_8)

            // File is marked to be excluded from Plugin Sources, so we delete it
            if (content.contains("// @excludeplugin")) {
                FileUtils.delete(file)
                return
            }

            // Code block that must be removed when compiling for paper
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