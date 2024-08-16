/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension;
import com.hypherionmc.orion.utils.Patcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class TaskActions {

    public static TaskActions INSTANCE = new TaskActions();

    public void cleanWorkspace(Logger logger, Project project) {
        project.delete(Constants.patcherWorkdir, Constants.patcherUpstream, new File(project.getRootProject().getRootDir(), "tmp"));
        logger.lifecycle("Cleaned up working directories");
    }

    public void generatePatches(Project project, Logger logger, OrionPortingExtension extension) throws Exception {
        logger.lifecycle("Generating Patches");
        for (String b : extension.getPortingBranches().get()) {
            Patcher.INSTANCE.generatePatches(project, b);
        }
    }

    public void setupWorkspace(Project project, Logger logger, OrionPortingExtension extension) throws Exception {
        if (!extension.getUpstreamBranch().isPresent() || extension.getUpstreamBranch().get().equalsIgnoreCase("INVALID")) {
            throw new GradleException("No upstream branch specified.");
        }

        if (extension.getPortingBranches().get().isEmpty())
            throw new GradleException("No porting branches specified");

        // Clean the working directories
        project.delete(Constants.patcherUpstream);
        project.delete(Constants.patcherWorkdir);

        // Check if current branch already has an upstream commit linked to it, and pull that instead
        String lastCommitId = null;
        if (Constants.patcherCommit.exists()) {
            lastCommitId = FileUtils.readFileToString(Constants.patcherCommit, StandardCharsets.UTF_8);
        }

        Patcher.INSTANCE.checkoutUpstreamBranch(project, extension.getUpstreamBranch().get(), extension, lastCommitId, true);
    }

    public void splitSources(Project project, Logger logger, OrionPortingExtension extension) throws IOException {
        if (!Files.exists(Constants.patcherWorkdir))
            throw new GradleException("Working Directory does NOT exist");

        for (String b : extension.getPortingBranches().get()) {
            File f = new File(project.getRootProject().getRootDir(), b);
            if (f.exists())
                FileUtils.deleteQuietly(f);

            FileUtils.copyDirectory(Constants.patcherWorkdir.resolve(b).toFile(), f);
        }
    }

    public void updateCommitSha(Project project, Logger logger, OrionPortingExtension extension) {
        if (!extension.getUpstreamBranch().isPresent() || extension.getUpstreamBranch().get().equalsIgnoreCase("INVALID")) {
            throw new GradleException("No upstream branch specified.");
        }

        try {
            Patcher.INSTANCE.checkoutUpstreamBranch(project, extension.getUpstreamBranch().get(), extension, null, false);
        } catch (Exception e) {
            logger.error("Failed to update commit ref", e);
        }
    }
}
