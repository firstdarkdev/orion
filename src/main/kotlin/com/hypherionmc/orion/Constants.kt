/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion

import com.hypherionmc.orion.plugin.OrionPlugin
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object Constants {
    
    // Strings
    const val TASK_GROUP  = "orion"
    const val PATCH_GROUP: String = "patches"
    val ORION_VERSION:String = OrionPlugin::class.java.`package`?.implementationVersion.toString()
    
    // Tool Deps
    const val AUTO_SERVICE: String = "com.google.auto.service:auto-service:1.1.1"
    const val LOMBOK: String = "org.projectlombok:lombok:1.18.34"
    const val NO_LOADER: String = "com.hypherionmc.noloaderthanks:noloaderthanks:1.0.6"

    // FDD Mavens
    const val MAVEN_URL: String = "https://maven.firstdark.dev/releases"
    const val MAVEN_SNAPSHOT_URL: String = "https://maven.firstdark.dev/snapshots"
    const val MAVEN_CENTRAL_URL: String = "https://mcentral.firstdark.dev/releases"

    // Porting Patcher
    @JvmField val patcherUpstream: Path = Paths.get("upstream")
    @JvmField val patcherWorkdir: Path = Paths.get("workspace")
    @JvmField val patcherCommit: File = File("commit.sha")
}