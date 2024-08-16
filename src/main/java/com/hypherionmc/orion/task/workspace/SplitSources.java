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

import java.io.IOException;

public class SplitSources extends DefaultTask {

    @TaskAction
    public void splitSources() throws IOException {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);
        if (extension == null)
            throw new GradleException("orionporting extension is not configured");

        TaskActions.INSTANCE.splitSources(getProject(), getLogger(), extension);
    }

}
