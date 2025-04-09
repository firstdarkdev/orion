/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.patches

import com.hypherionmc.orion.plugin.porting.OrionPortingExtension
import com.hypherionmc.orion.task.TaskActions
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class GeneratePatches: DefaultTask() {

    @TaskAction
    @Throws(Exception::class)
    fun generatePatches() {
        val extension = project.extensions.findByType(OrionPortingExtension::class.java)
            ?: throw GradleException("Cannot find orionporting extension on project")

        TaskActions.generatePatches(project, logger, extension)
    }

}