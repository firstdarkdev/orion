/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.patches

import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension
import com.hypherionmc.orion.task.TaskActions
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author HypherionSA
 *
 * Helper task to rebuild patches, when porting to a new Minecraft Version is being done
 */
open class RebuildPatches: DefaultTask() {

    @TaskAction
    @Throws(Exception::class)
    fun rebuildPatches() {
        val extension = project.rootProject.extensions.getByType(OrionPortingExtension::class.java)
            ?: throw GradleException("Cannot find orionporting extension on project")

        TaskActions.cleanWorkspace(logger, project)
        TaskActions.updateCommitSha(project, logger, extension)
        TaskActions.setupWorkspace(project, extension)

        for (b in extension.portingBranches.get()) {
            val f = File(project.rootProject.rootDir, b)
            val out = Constants.patcherWorkdir.resolve(b).toFile()

            if (out.exists())
                FileUtils.deleteQuietly(out)

            logger.lifecycle("⚡ Rebuilding patches...")
            FileUtils.copyDirectory(f, out)
        }

        TaskActions.generatePatches(project, logger, extension)
        TaskActions.cleanWorkspace(logger, project)
    }

}