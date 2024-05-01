/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author HypherionSA
 * Main plugin entrypoint
 */
public class OrionPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project target) {
        target.getExtensions().create("orion", OrionExtension.class, target);
    }
}
