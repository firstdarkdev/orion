/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.utils.DopplerUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * @author HypherionSA
 * Main plugin entrypoint
 */
public class OrionPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project target) {
        // Register and apply the gradle extension.
        OrionExtension extension = target.getExtensions().create("orion", OrionExtension.class);

        // Register the cleanup logic for multi-platform projects
        registerCleanup(target);

        // Doppler support. Pull ENV Variables from Doppler. Only done on the ROOT project
        target.getRootProject().afterEvaluate(c -> {
            if (!extension.getDopplerToken().get().equalsIgnoreCase("INVALID")) {
                DopplerUtils.installDopplerEnvironment(extension.getDopplerToken().get());
            }
        });

        target.allprojects(p -> p.afterEvaluate(c -> {
            // Set the group and version on all projects
            c.setGroup(p.getRootProject().getGroup());
            c.setVersion(extension.getVersioning().buildVersion());

            // Configure the artifact copying logic for multi-platform projects
            if (extension.getMultiProject().get())
                registerCopyLogic(p);

            // Add our releases maven
            if (extension.getEnableReleasesMaven().get()) {
                c.getRepositories().maven(m -> {
                    m.setName("First Dark Dev Maven");
                    m.setUrl(Constants.MAVEN_URL);
                });
            }

            // Add our snapshot maven
            if (extension.getEnableSnapshotsMaven().get()) {
                c.getRepositories().maven(m -> {
                   m.setName("First Dark Dev Snapshots Maven");
                   m.setUrl(Constants.MAVEN_SNAPSHOT_URL);
                });
            }

            // Add our mirror maven
            if (extension.getEnableMirrorMaven().get()) {
                c.getRepositories().maven(m -> {
                   m.setName("First Dark Dev Mirror");
                   m.setUrl(Constants.MAVEN_CENTRAL_URL);
                });
            }
        }));
    }

    /**
     * INTERNAL: Basically just deletes the "artifacts" directory for multi-platform projects
     * @param project The project to apply this logic to.
     */
    private void registerCleanup(Project project) {
        project.getTasks().getByName("clean").doLast(c -> {
            c.getLogger().lifecycle("Cleaning Artifact Directory");
            File outputDir = new File(project.getRootProject().getRootDir(), "artifacts");
            if (outputDir.exists()) {
                project.delete(outputDir);
            }
        });
    }

    /**
     * INTERNAL: Copies artifacts from the individual libs folders, into the central artifacts folder
     * @param project The project this logic must be applied to
     */
    @SuppressWarnings({"deprecation", "ResultOfMethodCallIgnored"})
    private void registerCopyLogic(Project project) {
        if (project.getName().equalsIgnoreCase("common") || project.getRootProject() == project)
            return;

        project.getTasks().getByName("build").doLast(c -> {
            File artifactDir = new File(project.getRootProject().getRootDir(), "artifacts");
            File libsDir = new File(project.getBuildDir(), "libs");
            File[] files = libsDir.listFiles();

            if (files == null)
                return;

            for (File f : files) {
                if (f.isDirectory())
                    continue;

                if (f.getName().contains("-dev-shadow") || f.getName().contains("-dev") || f.getName().contains("-all") || f.getName().contains("-slim")) {
                    f.delete();
                } else {
                    if (!artifactDir.exists())
                        artifactDir.mkdirs();

                    try {
                        Files.move(f.toPath(), new File(artifactDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        project.getLogger().error("Failed to copy artifact to output directory", e);
                        throw new GradleException(e.getMessage());
                    }
                }
            }
        });
    }
}
