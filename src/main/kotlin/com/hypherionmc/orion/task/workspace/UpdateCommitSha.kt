/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.workspace

import com.hypherionmc.orion.plugin.porting.OrionPortingExtension
import com.hypherionmc.orion.task.TaskActions
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class UpdateCommitSha: DefaultTask() {

    @TaskAction
    fun updateCommitSha() {
        val extension = project.extensions.getByType(OrionPortingExtension::class.java)
            ?: throw GradleException("orionporting extension is not configured")

        TaskActions.updateCommitSha(project, logger, extension)
    }

}