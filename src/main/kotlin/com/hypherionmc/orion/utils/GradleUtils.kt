/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.plugin.OrionExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.stream.Collectors

/**
 * @author Hypherionsa
 * Main plugin logic to apply versioning and mavens
 */
object GradleUtils {

    /**
     * Configure the gradle project, based on the plugin configuration
     *
     * @param target The project the plugin is applied to
     * @param extension The configured gradle extension
     */
    fun configureProject(target: Project, extension: OrionExtension) {
        // Fetch Doppler tokens
        if (target.rootProject == target && !extension.dopplerToken.get().equals("INVALID", ignoreCase = true)) {
            target.logger.lifecycle("\uD83D\uDD10 Installing Environment Variables from Doppler")
            DopplerUtils.installDopplerEnvironment(extension.dopplerToken.get())
        }

        registerCleanup(target.rootProject)

        target.allprojects {p ->
            p.group = p.rootProject.group
            p.version = extension.versioning.buildVersion()

            if (extension.multiProject.get())
                registerCopyLogic(p)

            // Add our releases maven
            if (extension.enableReleasesMaven.get()) {
                p.repositories.maven { m ->
                    m.name = "First Dark Dev Maven"
                    m.url = URI(Constants.MAVEN_URL)
                }
            }

            // Add our snapshots maven
            if (extension.enableSnapshotsMaven.get()) {
                p.repositories.maven { m ->
                    m.name = "First Dark Dev Snapshots Maven"
                    m.url = URI(Constants.MAVEN_SNAPSHOT_URL)
                }
            }

            // Add our mirror maven
            if (extension.enableMirrorMaven.get()) {
                p.repositories.maven { m ->
                    m.name = "First Dark Dev Mirror Maven"
                    m.url = URI(Constants.MAVEN_CENTRAL_URL)
                }
            }

            p.afterEvaluate { pp -> applyTools(pp, extension) }
        }
    }

    /**
     * Apply various built in tools to projects
     *
     * @param p The project the tools need to be applied to
     * @param extension The OrionExtension that was configured
     */
    private fun applyTools(p: Project, extension: OrionExtension) {
        if (extension.multiProject.get() && p.name.equals(p.rootProject.name, ignoreCase = true))
            return

        // Auto Service
        if (extension.tools.enableAutoService) {
            p.dependencies.add("compileOnly", Constants.AUTO_SERVICE)
            p.dependencies.add("annotationProcessor", Constants.AUTO_SERVICE)
        }

        // Lombok
        if (extension.tools.enableLombok) {
            p.dependencies.add("compileOnly", Constants.LOMBOK)
            p.dependencies.add("annotationProcessor", Constants.LOMBOK)
        }

        // NoLoader
        if (extension.tools.enableNoLoader) {
            p.dependencies.add("compileOnly", Constants.NO_LOADER)
        }
    }

    /**
     * INTERNAL: Basically just deletes the "artifacts" directory for multi-platform projects
     *
     * @param target The project to apply this logic to.
     */
    private fun registerCleanup(target: Project) {
        target.tasks.getByName("clean").doLast { c ->
            c.logger.lifecycle("\uD83E\uDDF9 Cleaning Artifacts Directory")
            val outputDir = File(target.rootProject.rootDir, "artifacts")

            if (outputDir.exists())
                target.delete(outputDir)
        }
    }

    /**
     * INTERNAL: Copies artifacts from the individual libs folders, into the central artifacts folder
     *
     * @param target The project this logic must be applied to
     */
    private fun registerCopyLogic(target: Project) {
        if (target.name.equals("common", ignoreCase = true) || target.rootProject == target)
            return

        target.afterEvaluate { _ ->
            target.tasks.getByName("build").doLast { _ ->
                val artifactDir = File(target.rootProject.rootDir, "artifacts")
                val libsDir = File(target.layout.buildDirectory.asFile.get(), "libs")
                val files = libsDir.listFiles() ?: return@doLast

                for (f in Arrays.stream(files).filter { f -> !f.isDirectory }.collect(Collectors.toList())) {
                    if (f.name.contains("-dev-shadow") || f.name.contains("-dev") || f.name.contains("-all") || f.name.contains("-slim")) {
                        f.delete()
                        continue
                    }

                    if (!artifactDir.exists())
                        artifactDir.mkdirs()

                    try {
                        Files.move(f.toPath(), File(artifactDir, f.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: Exception) {
                        target.logger.error("Failed to copy artifact ${f.name} to output directory", e)
                        throw GradleException("Failed to copy artifact ${f.name} to output directory")
                    }
                }
            }
        }
    }

}