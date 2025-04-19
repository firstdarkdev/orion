/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.paper

import com.hypherionmc.orion.task.paper.BeforeCompileTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

/**
 * @author HypherionSA
 *
 * Plugin for paper projects, to handle compiling for Paper against modded minecraft code
 */
class OrigamiPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        // Register the extension
        target.extensions.create("origami", OrigamiExtension::class.java, target)

        // Register the task that will take care of the cleanup process, before compiling
        val preparePluginSources: TaskProvider<BeforeCompileTask> = target.tasks.register("preparePluginSources", BeforeCompileTask::class.java)
        target.tasks.withType(JavaCompile::class.java).forEach { task -> task.dependsOn(preparePluginSources) }
    }
}