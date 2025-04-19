/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author HypherionSA
 *
 * Main plugin entrypoint
 */
class OrionPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.extensions.create("orion", OrionExtension::class.java, target)
    }

}