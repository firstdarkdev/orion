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
import org.gradle.api.tasks.SourceSetContainer
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

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val sourceSet = sourceSets.getByName("main")
        val paperSourceSet = sourceSets.getByName(extension.sourceSet.get())

        val javaSrcDir = sourceSet.java.srcDirs.first()
        val resourcesSrcDir = sourceSet.resources.srcDirs.first()

        project.logger.lifecycle("⚡ Preparing Paper Sources")

        // Set up the temporary processing folder
        val destFolder = project.layout.buildDirectory.dir("processedSources/main").get().asFile
        if (destFolder.exists())
            FileUtils.deleteDirectory(destFolder)

        FileUtils.copyDirectory(javaSrcDir, File(destFolder, "java"))
        FileUtils.copyDirectory(resourcesSrcDir, File(destFolder, "resources"))

        val removedFiles = mutableSetOf<String>()

        // Filter out excluded code packages
        for (excludedPackage in extension.excludedPackages.get()) {
            val pkg = File(destFolder, "java/${excludedPackage.replace('.', '/')}")
            if (pkg.exists()) {
                removedFiles.add(excludedPackage.replace('.', '/'))
            }
        }

        // Filter out excluded resources
        for (excludedResource in extension.excludedResources.get()) {
            val res = File(destFolder, "resources/${excludedResource}")
            if (res.exists()) {
                removedFiles.add(excludedResource)
            }
        }

        processComments(destFolder, removedFiles)

        val newSourceDirs = mutableListOf(
            File(destFolder, "java"),
            File(destFolder, "resources")
        )

        val compileTaskName = paperSourceSet.getCompileTaskName("java")
        val compileTask = project.tasks.named(compileTaskName, JavaCompile::class.java).get()

        newSourceDirs.forEach { compileTask.source(it) }

        val markerDir = File(destFolder, "marker.txt")
        if (markerDir.exists())
            FileUtils.delete(markerDir)

        FileUtils.write(markerDir, removedFiles.joinToString("\n"), StandardCharsets.UTF_8)
    }

    /**
     * Process code comments that handle excluding certain pieces of code, or even entire classes
     *
     * @param sourceDir The Directory that is being processed
     */
    private fun processComments(sourceDir: File, removedFiles: MutableSet<String>) {
        try {
            Files.walkFileTree(sourceDir.toPath(), object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                        stripSpecialCode(file.toFile(), removedFiles)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) {
            throw GradleException(e.message ?: "Error while reading file", e)
        }
    }

    /**
     * Strip code and comments from Source Code
     *
     * @param file The File that is being processed
     */
    private fun stripSpecialCode(file: File, removedFiles: MutableSet<String>) {
        try {
            val content = FileUtils.readFileToString(file, StandardCharsets.UTF_8)

            // File is marked to be excluded from Plugin Sources, so we delete it
            if (content.contains("// @excludeplugin")) {
                val normalized = file.absolutePath.replace(File.separatorChar, '/')
                val idx = normalized.indexOf("/java/")

                if (idx != -1) {
                    val relative = normalized.substring(idx + "/java/".length)
                    removedFiles.add(relative)
                }
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