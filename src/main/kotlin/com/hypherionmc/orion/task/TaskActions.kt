/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task

import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension
import com.hypherionmc.orion.utils.Patcher
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * @author HypherionSA
 *
 * Helper/Utility class to reduce duplicate code that are shared between tasks
 */
object TaskActions {

    /**
     * Clean up porting workspace directories
     *
     * @param logger The Gradle Logger that is currently used
     * @param project The Gradle Project that is currently being processed
     */
    fun cleanWorkspace(logger: Logger, project: Project) {
        project.delete(Constants.patcherWorkdir, Constants.patcherUpstream, File(project.rootProject.rootDir, "tmp"))
        logger.lifecycle("\uD83E\uDDF9 Cleaned up working directories")
    }

    /**
     * Generate patches for porting code between versions
     *
     * @param project The Gradle Project that is currently being processed
     * @param logger The Gradle Logger that is currently used
     * @param extension The OrionPortingExtension that was configured for use in this task
     */
    @Throws(Exception::class)
    fun generatePatches(project: Project, logger: Logger, extension: OrionPortingExtension) {
        logger.lifecycle("⚡ Generating patches")

        for (b in extension.portingBranches.get()) {
            Patcher.generatePatches(project, b)
        }
    }

    /**
     * Set up a porting workspace
     *
     * @param project The Gradle Project that is currently being processed
     * @param extension The OrionPortingExtension that was configured for use in this task
     */
    @Throws(Exception::class)
    fun setupWorkspace(project: Project, extension: OrionPortingExtension) {
        if (!extension.upstreamBranch.isPresent || extension.upstreamBranch.get().equals("INVALID", ignoreCase = true)) {
            throw GradleException("No Upstream branch specified")
        }

        if (extension.portingBranches.get().isEmpty()) {
            throw GradleException("No Porting branch specified")
        }

        project.logger.lifecycle("⚡ Setting up workspace")

        // Clean the working directories
        project.delete(Constants.patcherWorkdir, Constants.patcherUpstream)

        // Check if current branch already has an upstream commit linked to it, and pull that instead
        var lastCommitId: String? = null
        if (Constants.patcherCommit.exists()) {
            lastCommitId = FileUtils.readFileToString(Constants.patcherCommit, StandardCharsets.UTF_8)
        }

        Patcher.checkoutUpstreamBranch(project, extension.upstreamBranch.get(), extension, lastCommitId, true)
    }

    /**
     * Split porting sources from the working directory, into standalone code
     *
     * @param project The Gradle Project that is currently being processed
     * @param extension The OrionPortingExtension that was configured for use in this task
     */
    fun splitSources(project: Project, extension: OrionPortingExtension) {
        if (!Files.exists(Constants.patcherWorkdir))
            throw GradleException("Working directory does not exist")

        project.logger.lifecycle("⚡ Splitting sources into individual directories")

        for (b in extension.portingBranches.get()) {
            val f = File(project.rootProject.rootDir, b)
            if (f.exists())
                FileUtils.deleteQuietly(f)

            FileUtils.copyDirectory(Constants.patcherWorkdir.resolve(b).toFile(), f)
        }
    }

    /**
     * Update the upstream branch commit reference that is being used for the base sources directory
     * during porting
     *
     * @param project The Gradle Project that is currently being processed
     * @param logger The Gradle Logger that is currently used
     * @param extension The OrionPortingExtension that was configured for use in this task
     */
    fun updateCommitSha(project: Project, logger: Logger, extension: OrionPortingExtension) {
        if (!extension.upstreamBranch.isPresent || extension.upstreamBranch.get().equals("INVALID", ignoreCase = true)) {
            throw GradleException("No upstream branch specified.")
        }

        project.logger.lifecycle("⚡ Updating Commit Reference")

        try {
            Patcher.checkoutUpstreamBranch(project, extension.upstreamBranch.get(), extension, null, false)
        } catch (e: Exception) {
            logger.error("Failed to update commit ref", e)
        }
    }

}