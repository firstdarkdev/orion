/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class OrionPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create("orion", OrionExtension::class.java, target)
    }

}