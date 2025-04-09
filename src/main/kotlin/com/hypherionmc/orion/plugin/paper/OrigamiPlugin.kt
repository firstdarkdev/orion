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

class OrigamiPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create("origami", OrigamiExtension::class.java, target)

        val preparePluginSources: TaskProvider<BeforeCompileTask> = target.tasks.register("preparePluginSources", BeforeCompileTask::class.java)
        target.tasks.withType(JavaCompile::class.java).forEach { task -> task.dependsOn(preparePluginSources) }
    }
}