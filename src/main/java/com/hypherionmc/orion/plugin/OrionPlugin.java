/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin;

import com.hypherionmc.orion.task.CleanWorkspace;
import com.hypherionmc.orion.task.GeneratePatches;
import com.hypherionmc.orion.task.SetupWorkspace;
import com.hypherionmc.orion.task.UpdateCommitSha;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author HypherionSA
 * Main plugin entrypoint
 */
public class OrionPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project target) {
        target.getExtensions().create("orion", OrionExtension.class, target);

        // Register the porting extension and tasks
        target.getRootProject().getExtensions().create("orionporting", OrionPortingExtension.class, target.getRootProject());

        final TaskProvider<SetupWorkspace> setupWorkspaceTask = target.getRootProject().getTasks().register("setupWorkspace", SetupWorkspace.class);
        setupWorkspaceTask.configure(c -> c.setGroup("orion"));

        final TaskProvider<GeneratePatches> genPatchesTask = target.getRootProject().getTasks().register("generatePatches", GeneratePatches.class);
        genPatchesTask.configure(c -> c.setGroup("orion"));

        final TaskProvider<CleanWorkspace> cleanWorkspace = target.getRootProject().getTasks().register("cleanWorkspace", CleanWorkspace.class);
        cleanWorkspace.configure(c -> c.setGroup("orion"));

        final TaskProvider<UpdateCommitSha> updateCommitRef = target.getRootProject().getTasks().register("updateCommitRef", UpdateCommitSha.class);
        updateCommitRef.configure(c -> c.setGroup("orion"));
    }
}
