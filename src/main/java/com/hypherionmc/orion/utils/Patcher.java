/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import com.hypherionmc.orion.Constants;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author HypherionSA
 * Patcher class to manage generating and applying patches, and working with branches
 */
public class Patcher {

    public static final Patcher INSTANCE = new Patcher();

    Patcher() {}

    /**
     * Pull the upstream (or dev) branch into the upstream folder
     * This will also apply patches if any are found
     * @param project The project the plugin is applied to
     * @param branch The branch to check out, if no commitId is specified
     * @param commitId Optional commit id, to check out a specific commit
     * @throws Exception Shit went wrong
     */
    public void checkoutUpstreamBranch(Project project, String branch, @Nullable String commitId, boolean applyPatches) throws Exception {
        // Get the repository info
        Repository repository = new FileRepositoryBuilder().setGitDir(new File(project.getRootProject().getRootDir(), ".git")).build();
        ObjectId devBranchId = repository.resolve(commitId == null ? branch : commitId);

        project.getLogger().lifecycle("Pulling '{}' from into upstream directory", branch);

        // Checkout the branch into the upstream directory
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(devBranchId);

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String filePath = treeWalk.getPathString();
                ObjectId objectId = treeWalk.getObjectId(0);
                byte[] fileData = repository.open(objectId).getBytes();

                File targetFile = new File(repository.getWorkTree(), Constants.patcherUpstream + File.separator + filePath);
                targetFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(fileData);
                }
            }
        }

        // Close the repository and RevWalk
        repository.close();
        revWalk.close();

        // If this is a fresh pull, or update, write the commit hash for later retrieval
        if (commitId == null) {
            FileUtils.write(Constants.patcherCommit, devBranchId.getName(), StandardCharsets.UTF_8);
        }

        if (applyPatches) {
            // Apply Patches
            applyPatches(project);
        }
    }

    /**
     * Generate patches for changes between the upstream branch and working directory
     * @param project The project the plugin is applied to
     * @throws Exception Shit went wrong
     */
    public void generatePatches(Project project) throws Exception {
        DiffOperation.Builder builder = DiffOperation.builder()
                .logTo(new LoggingOutputStream(project.getLogger(), LogLevel.LIFECYCLE))
                .aPath(Constants.patcherUpstream)
                .bPath(Constants.patcherWorkdir)
                .outputPath(new File(project.getRootProject().getRootDir(), "patches").toPath(), null)
                .autoHeader(false)
                .summary(true)
                .aPrefix("a/")
                .bPrefix("b/")
                .lineEnding(System.lineSeparator());

        CliOperation.Result<DiffOperation.DiffSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        } else {
            project.getLogger().lifecycle("Generated Patches successfully");
        }
    }

    /**
     * Apply patches from the patches directory, into the working directory
     * @param project The project the plugin is applied to
     * @throws Exception Shit went wrong
     */
    public void applyPatches(Project project) throws Exception {
        // Working directories
        File base = new File(project.getRootProject().getRootDir(), "upstream");
        File patches = new File(project.getRootProject().getRootDir(), "patches");
        File out = new File(project.getRootProject().getRootDir(), "dev");
        File rejects = new File(project.getRootProject().getRootDir(), "rejects");

        // Check if any patches have been generated. If not, we copy the upstream folder to the dev folder
        if (!hasPatches(patches)) {
            project.getLogger().lifecycle("Copying upstream branch into work directory");
            FileUtils.copyDirectory(Constants.patcherUpstream.toFile(), Constants.patcherWorkdir.toFile());
            return;
        }

        // Set up the patch operation
        PatchOperation.Builder builder = PatchOperation.builder()
                .logTo(new LoggingOutputStream(project.getLogger(), LogLevel.LIFECYCLE))
                .basePath(base.toPath())
                .patchesPath(patches.toPath())
                .outputPath(out.toPath())
                .rejectsPath(rejects.toPath())
                .summary(true)
                .mode(PatchMode.OFFSET)
                .level(codechicken.diffpatch.util.LogLevel.ERROR)
                .lineEnding(System.lineSeparator());

        builder.helpCallback(System.out::println);

        CliOperation.Result<PatchOperation.PatchesSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
        if (exit != 0) {
            throw new RuntimeException("Patches failed to apply.");
        }

        project.getLogger().lifecycle("Applied Patches successfully");
    }

    /**
     * Helper method to check if the patches folder has any patches to apply
     * @param patchesDir The directory containing the patches
     * @return True if yes, False if no
     */
    private boolean hasPatches(File patchesDir) {
        if (patchesDir.exists()) {
            File[] f = patchesDir.listFiles();
            return f != null && f.length != 0;
        }

        return false;
    }
}
