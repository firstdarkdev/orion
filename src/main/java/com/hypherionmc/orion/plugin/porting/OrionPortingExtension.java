/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.porting;

import codechicken.diffpatch.util.PatchMode;
import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author HypherionSA
 * Extension to manage porting between branches with patch files
 */
@Getter
public class OrionPortingExtension {

    private final Property<String> upstreamBranch;
    private final Project project;
    private final List<String> portingBranches = new ArrayList<>();
    private final Property<PatchMode> patchMode;

    public OrionPortingExtension(Project project) {
        this.project = project;
        this.upstreamBranch = project.getObjects().property(String.class).convention("INVALID");
        this.patchMode = project.getObjects().property(PatchMode.class).convention(PatchMode.EXACT);
    }

    public void porting(String value) {
        portingBranches.add(value);
    }

    public void porting(String... values) {
        portingBranches.addAll(Arrays.stream(values).collect(Collectors.toList()));
    }
}
