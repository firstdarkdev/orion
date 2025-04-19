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

/**
 * @author HypherionSA
 *
 * Task to split porting code, into usable projects for compiling, without needing to set up a full
 * porting environment
 */
open class SplitSources: DefaultTask() {

    @TaskAction
    fun splitSources() {
        val extension = project.extensions.getByType(OrionPortingExtension::class.java)
            ?: throw GradleException("orionporting extension is not configured")

        TaskActions.splitSources(project, extension)
    }

}