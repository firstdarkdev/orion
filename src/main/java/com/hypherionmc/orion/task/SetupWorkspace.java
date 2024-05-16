/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.plugin.OrionPortingExtension;
import com.hypherionmc.orion.utils.Patcher;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

        if (!extension.getUpstreamBranch().isPresent() || extension.getUpstreamBranch().get().equalsIgnoreCase("INVALID")) {
            throw new GradleException("No upstream branch specified.");
        }

        if (extension.getPortingBranches().isEmpty())
            throw new GradleException("No porting branches specified");

        // Clean the working directories
        getProject().delete(Constants.patcherUpstream);
        getProject().delete(Constants.patcherWorkdir);

        // Check if current branch already has an upstream commit linked to it, and pull that instead
        String lastCommitId = null;
        if (Constants.patcherCommit.exists()) {
            lastCommitId = FileUtils.readFileToString(Constants.patcherCommit, StandardCharsets.UTF_8);
        }

        Patcher.INSTANCE.checkoutUpstreamBranch(getProject(), extension.getUpstreamBranch().get(), extension, lastCommitId, true);
    }

}
