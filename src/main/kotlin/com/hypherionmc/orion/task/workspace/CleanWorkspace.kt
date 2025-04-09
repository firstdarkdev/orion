/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.workspace

import com.hypherionmc.orion.task.TaskActions
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CleanWorkspace: DefaultTask() {

    @TaskAction
    fun cleanupWorkspace() {
        TaskActions.cleanWorkspace(logger, project)
    }

}