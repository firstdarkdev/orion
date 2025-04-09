/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

import codechicken.diffpatch.cli.DiffOperation
import codechicken.diffpatch.cli.PatchOperation
import codechicken.diffpatch.util.LoggingOutputStream
import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.io.path.absolutePathString

/**
 * @author HypherionSA
 * Patcher class to manage generating and applying patches, and working with branches
 */
object Patcher {

    /**
     * Pull the upstream (or dev) branch into the upstream folder
     * This will also apply patches if any are found
     *
     * @param project The project the plugin is applied to
     * @param branch The branch to check out, if no commitId is specified
     * @param commitId Optional commit id, to check out a specific commit
     * @throws Exception Shit went wrong
     */
    @Throws(Exception::class)
    fun checkoutUpstreamBranch(project: Project, branch: String, extension: OrionPortingExtension, commitId: String?, applyPatches: Boolean) {
        val repository = FileRepositoryBuilder().setGitDir(File(project.rootProject.rootDir, ".git")).build()
        val devBranchId = repository.resolve(commitId ?: branch)

        project.logger.lifecycle("⚡ Pulling from '$branch' into upstream directory")

        // Checkout the branch into the upstream directory
        val refWalk = RevWalk(repository)
        val commit = refWalk.parseCommit(devBranchId)

        TreeWalk(repository).use { treeWalk ->
            treeWalk.addTree(commit.tree)
            treeWalk.isRecursive = true

            while (treeWalk.next()) {
                val filePath = treeWalk.pathString
                val objectId = treeWalk.getObjectId(0)

                try {
                    val fileData = repository.open(objectId).bytes
                    val targetFile = File(repository.workTree, Constants.patcherUpstream.toString() + File.separator + filePath)
                    targetFile.parentFile.mkdirs()

                    FileOutputStream(targetFile).use { fos -> fos.write(fileData) }
                } catch (e: IOException) {
                    project.logger.warn("Failed to fully parse commit {}", objectId, e)
                }
            }
        }

        // Close the repository and RevWalk
        repository.close()
        refWalk.close()

        // If this is a fresh pull or update, write the commit hash for later retrieval
        if (commitId == null) {
            FileUtils.write(Constants.patcherCommit, devBranchId.name(), StandardCharsets.UTF_8)
            repository.close()
        }

        if (applyPatches) {
            for (b in extension.portingBranches.get()) {
                applyPatches(project, b, extension)
            }
        }
    }

    /**
     * Generate patches for changes between the upstream branch and working directory
     *
     * @param project The project the plugin is applied to
     * @throws Exception Shit went wrong
     */
    @Throws(Exception::class)
    fun generatePatches(project: Project, workingDir: String) {
        val builder = DiffOperation.builder()
            .logTo(LoggingOutputStream(project.logger, LogLevel.LIFECYCLE))
            .aPath(Constants.patcherUpstream)
            .bPath(File(project.rootProject.rootDir, Constants.patcherWorkdir.toString() + File.separator + workingDir).toPath())
            .outputPath(File(project.rootProject.rootDir, "patches/${workingDir}").toPath())
            .autoHeader(false)
            .summary(true)
            .aPrefix("a/")
            .bPrefix("b/")
            .lineEnding(System.lineSeparator())

        val ignored = arrayOf(".idea", ".gradle")

        for (i in ignored) {
            builder.ignorePrefix(i)
        }

        val result = builder.build().operate()
        val exit: Int = result.exit

        if (exit != 0 && exit != 1) {
            throw RuntimeException("DiffPatch failed with exit code $exit")
        } else {
            project.logger.lifecycle("\uD83C\uDF89 Generated Patches Successfully")
            cleanPatchesDir(File("patches"))
        }
    }

    /**
     * Apply patches from the patches directory, into the working directory
     *
     * @param project The project the plugin is applied to
     * @throws Exception Shit went wrong
     */
    @Throws(Exception::class)
    fun applyPatches(project: Project, workingDir: String, extension: OrionPortingExtension) {
        // Working directories
        val base = File(project.rootProject.rootDir, "upstream")
        val patches = File(project.rootProject.rootDir, "patches/$workingDir")
        val out = File(project.rootProject.rootDir, Constants.patcherWorkdir.toString() + File.separator + workingDir)
        val rejects = File(project.rootProject.rootDir, "rejects/$workingDir")

        // Check if any patches have been generated. If not, we copy the upstream folder to the dev folder
        if (!hasPatches(patches)) {
            project.logger.lifecycle("⚡ Copying upstream branch into {} directory", workingDir)
            FileUtils.copyDirectory(Constants.patcherUpstream.toFile(), out)
            return
        }

        project.logger.lifecycle("⚡ Patching {}", workingDir)

        // Set up the patch operation
        val builder = PatchOperation.builder()
            .logTo(LoggingOutputStream(project.logger, LogLevel.LIFECYCLE))
            .basePath(base.toPath())
            .patchesPath(patches.toPath())
            .outputPath(out.toPath())
            .rejectsPath(rejects.toPath())
            .summary(true)
            .mode(extension.patchMode.get())
            .level(codechicken.diffpatch.util.LogLevel.ERROR)
            .lineEnding(System.lineSeparator())

        builder.helpCallback { x: PrintStream? -> println(x) }

        val result = builder.build().operate()
        val exit = result.exit

        if (exit != 0 && exit != 1) {
            throw RuntimeException("DiffPatch failed with exit code: $exit")
        }
        if (exit != 0) {
            project.logger.error("Patched failed to apply for {}", workingDir)
        }

        project.logger.lifecycle("\uD83C\uDF89 Applied Patches successfully")
    }

    /**
     * Helper method to check if the patches folder has any patches to apply
     *
     * @param patchesDir The directory containing the patches
     * @return True if yes, False if no
     */
    private fun hasPatches(patchesDir: File): Boolean {
        if (patchesDir.exists()) {
            val f = patchesDir.listFiles()
            return f != null && f.isNotEmpty()
        }

        return false
    }

    /**
     * Clean shit from patches dir that shouldn't be there
     * @param dir The output directory to clean
     */
    private fun cleanPatchesDir(dir: File) {
        val ignored: List<String> = mutableListOf(".idea", ".gradle", "build", "artifacts")

        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                cleanPatchesDir(f)
                continue
            }

            if (ignored.contains(f.name)) FileUtils.deleteQuietly(f)
        }

        if (ignored.contains(dir.name)) FileUtils.deleteQuietly(dir)
    }

}