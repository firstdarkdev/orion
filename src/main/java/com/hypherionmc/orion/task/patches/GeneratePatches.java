/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.patches;

import com.hypherionmc.orion.plugin.porting.OrionPortingExtension;
import com.hypherionmc.orion.task.TaskActions;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

/**
 * @author HypherionSA
 * Task to generate patches between the upstream branch and working directory
 */
public class GeneratePatches extends DefaultTask {

    @TaskAction
    public void generatePatches() throws Exception {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);

        if (extension == null)
            throw new GradleException("Cannot find orionporting extension on project");

        TaskActions.INSTANCE.generatePatches(getProject(), getLogger(), extension);
    }

}
