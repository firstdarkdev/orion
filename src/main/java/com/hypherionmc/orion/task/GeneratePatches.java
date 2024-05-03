/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task;

import com.hypherionmc.orion.utils.Patcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * @author HypherionSA
 * Task to generate patches between the upstream branch and working directory
 */
public class GeneratePatches extends DefaultTask {

    @TaskAction
    public void generatePatches() throws Exception {
        getLogger().lifecycle("Generating Patches");
        Patcher.INSTANCE.generatePatches(getProject());
    }

}
