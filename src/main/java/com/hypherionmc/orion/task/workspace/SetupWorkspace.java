/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.workspace;

import com.hypherionmc.orion.plugin.porting.OrionPortingExtension;
import com.hypherionmc.orion.task.TaskActions;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * @author HypherionSA
 * Task to prepare a porting workspace. Includes pulling the upstream branch, and applying patches if any
 */
public class SetupWorkspace extends DefaultTask {

    @TaskAction
    public void setupWorkspace() throws Exception {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);
        if (extension == null)
            throw new GradleException("orionporting extension is not configured");

        TaskActions.INSTANCE.setupWorkspace(getProject(), getLogger(), extension);
    }

}
