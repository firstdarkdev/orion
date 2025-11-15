/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.paper

import com.hypherionmc.orion.task.paper.AfterCompileTask
import com.hypherionmc.orion.task.paper.BeforeCompileTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * @author HypherionSA
 *
 * Plugin for paper projects, to handle compiling for Paper against modded minecraft code
 */
class OrigamiPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        // Register the extension
        target.extensions.create("origami", OrigamiExtension::class.java, target)

        // Register the task that will take care of the cleanup process before compiling
        val preparePluginSources: TaskProvider<BeforeCompileTask> = target.tasks.register("preparePluginSources", BeforeCompileTask::class.java)
        val cleanSourcesTask: TaskProvider<AfterCompileTask> = target.tasks.register("cleanPluginSources", AfterCompileTask::class.java)

        target.afterEvaluate {
            val ext = it.extensions.getByType(OrigamiExtension::class.java)
            val sourceSets = it.extensions.getByType(SourceSetContainer::class.java)
            val sourceSetName = ext.sourceSet.get()
            val sourceSet = sourceSets.getByName(sourceSetName)
            val mainSourceSet = sourceSets.getByName("main")

            // Add Main Source set compile, runtime and annotation processors
            sourceSet.compileClasspath += mainSourceSet.compileClasspath
            sourceSet.annotationProcessorPath += mainSourceSet.annotationProcessorPath
            sourceSet.runtimeClasspath += mainSourceSet.runtimeClasspath

            val javaCompile = it.tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java).get()
            javaCompile.dependsOn(preparePluginSources)
            javaCompile.finalizedBy(cleanSourcesTask)

            // Temporary directory
            val paperClassesDirs = target.layout.buildDirectory.dir("classes/java/paper")

            // We need to clean out the already compiled "common" classes, because they will be duplicate and need to change anyway
            val cleanMainOutput = target.tasks.register("cleanMainOutput") { tt ->
                tt.doLast {
                    val mainOutput = target.layout.buildDirectory.dir("classes/java/main").get().asFile
                    if (mainOutput.exists()) {
                        mainOutput.deleteRecursively()
                    }
                }
            }

            // Set up the Paper compile task with the new sources
            target.tasks.withType(Jar::class.java).named("${sourceSetName}Jar").configure { itt ->
                itt.dependsOn(cleanMainOutput)
                itt.from(paperClassesDirs)
            }
        }
    }
}