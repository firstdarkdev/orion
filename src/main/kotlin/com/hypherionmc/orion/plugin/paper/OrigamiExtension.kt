/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.paper

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

open class OrigamiExtension(project: Project) {

    val excludedPackages: ListProperty<String> = project.objects.listProperty(String::class.java).convention(emptyList())
    val excludedResources: ListProperty<String> = project.objects.listProperty(String::class.java).convention(emptyList())
    val commonProject: Property<String> = project.objects.property(String::class.java).convention("Common")


}