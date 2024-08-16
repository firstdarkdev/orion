/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.workspace;

import com.hypherionmc.orion.task.TaskActions;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * @author HypherionSA
 * Cleans the porting work space. Removes the working folders and temporary folders, but leaves the patches intact
 */
public class CleanWorkspace extends DefaultTask {

    @TaskAction
    public void cleanupWorkspace() {
        TaskActions.INSTANCE.cleanWorkspace(getLogger(), getProject());
    }

}
