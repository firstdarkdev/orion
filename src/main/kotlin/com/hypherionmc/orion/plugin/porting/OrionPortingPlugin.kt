/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.porting

import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.task.patches.GeneratePatches
import com.hypherionmc.orion.task.patches.RebuildPatches
import com.hypherionmc.orion.task.workspace.CleanWorkspace
import com.hypherionmc.orion.task.workspace.SetupWorkspace
import com.hypherionmc.orion.task.workspace.SplitSources
import com.hypherionmc.orion.task.workspace.UpdateCommitSha
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author HypherionSA
 * Main porting plugin entrypoint
 */
class OrionPortingPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        // Register the porting extension and tasks
        target.rootProject.extensions.create("orionporting", OrionPortingExtension::class.java, target.rootProject)

        // Configure Tasks
        target.rootProject.tasks.register("setupWorkspace", SetupWorkspace::class.java).configure { c -> c.group = Constants.TASK_GROUP }
        target.rootProject.tasks.register("cleanWorkspace", CleanWorkspace::class.java).configure { c -> c.group = Constants.TASK_GROUP }
        target.rootProject.tasks.register("updateCommitRef", UpdateCommitSha::class.java).configure { c -> c.group = Constants.TASK_GROUP }
        target.rootProject.tasks.register("splitSources", SplitSources::class.java).configure { c -> c.group = Constants.TASK_GROUP }

        // Patching Tasks
        target.rootProject.tasks.register("generatePatches", GeneratePatches::class.java).configure { c -> c.group = Constants.PATCH_GROUP }
        target.rootProject.tasks.register("rebuildPatches", RebuildPatches::class.java).configure { c -> c.group = Constants.PATCH_GROUP }
    }

}