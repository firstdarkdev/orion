/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task;

import com.hypherionmc.orion.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * @author HypherionSA
 * Cleans the porting work space. Removes the working folders and temporary folders, but leaves the patches intact
 */
public class CleanWorkspace extends DefaultTask {

    @TaskAction
    public void cleanupWorkspace() {
        getProject().delete(Constants.patcherWorkdir, Constants.patcherUpstream, new File(getProject().getRootProject().getRootDir(), "tmp").toPath());
        getLogger().lifecycle("Cleaned up working directories");
    }

}
