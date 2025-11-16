/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion

import com.hypherionmc.orion.plugin.OrionPlugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Path

object Constants {
    
    // Strings
    const val TASK_GROUP  = "orion"
    const val PATCH_GROUP: String = "patches"
    val ORION_VERSION:String = OrionPlugin::class.java.`package`?.implementationVersion.toString()
    
    // Tool Deps
    const val AUTO_SERVICE: String = "com.google.auto.service:auto-service:1.1.1"
    const val LOMBOK: String = "org.projectlombok:lombok:1.18.34"
    const val NO_LOADER: String = "com.hypherionmc.noloaderthanks:noloaderthanks:1.0.6"
    const val NEOJAR: String = "com.hypherionmc.neojar:neojar:1.0.3"

    // FDD Mavens
    const val MAVEN_URL: String = "https://maven.firstdark.dev/releases"
    const val MAVEN_SNAPSHOT_URL: String = "https://maven.firstdark.dev/snapshots"
    const val MAVEN_CENTRAL_URL: String = "https://mcentral.firstdark.dev/releases"

    // Porting Patcher
    fun patcherUpstream(project: Project): Path {
        return project.rootProject.rootDir.toPath().resolve(".orion").resolve("upstream")
    }

    fun patcherWorkdir(project: Project): Path {
        return project.rootProject.rootDir.toPath().resolve("workspace")
    }

    fun patcherCommit(project: Project): File {
        return project.rootProject.rootDir.resolve("commit.sha")
    }
}