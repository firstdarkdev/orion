/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.porting

import codechicken.diffpatch.util.PatchMode
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.util.*
import java.util.stream.Collectors

/**
 * @author HypherionSA
 *
 * Gradle extension to handle setting up a porting environment for Minecraft Mods
 */
open class OrionPortingExtension(project: Project) {

    val upstreamBranch: Property<String> = project.objects.property(String::class.java).convention("INVALID")
    val portingBranches: ListProperty<String> = project.objects.listProperty(String::class.java).convention(emptyList())
    val patchMode: Property<PatchMode> = project.objects.property(PatchMode::class.java).convention(PatchMode.EXACT)

    fun porting(value: String) {
        portingBranches.add(value)
    }

    fun porting(vararg values: String) {
        portingBranches.addAll(Arrays.stream(values).collect(Collectors.toList()))
    }

}