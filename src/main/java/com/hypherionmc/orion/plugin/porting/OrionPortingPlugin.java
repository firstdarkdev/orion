/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin.porting;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.task.*;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * @author HypherionSA
 * Main porting plugin entrypoint
 */
public class OrionPortingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        // Register the porting extension and tasks
        target.getRootProject().getExtensions().create("orionporting", OrionPortingExtension.class, target.getRootProject());

        // Configure tasks
        target.getRootProject().getTasks().register("setupWorkspace", SetupWorkspace.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
        target.getRootProject().getTasks().register("generatePatches", GeneratePatches.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
        target.getRootProject().getTasks().register("cleanWorkspace", CleanWorkspace.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
        target.getRootProject().getTasks().register("updateCommitRef", UpdateCommitSha.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
        target.getRootProject().getTasks().register("splitSources", SplitSources.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
        target.getRootProject().getTasks().register("rebuildPatches", RebuildPatches.class).configure(c -> c.setGroup(Constants.TASK_GROUP));
    }
}
