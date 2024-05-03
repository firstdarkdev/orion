/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin;

import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

/**
 * @author HypherionSA
 * Extension to manage porting between branches with patch files
 */
@Getter
public class OrionPortingExtension {

    private final Property<String> upstreamBranch;

    private final Project project;

    public OrionPortingExtension(Project project) {
        this.project = project;
        this.upstreamBranch = project.getObjects().property(String.class).convention("INVALID");
    }
}
