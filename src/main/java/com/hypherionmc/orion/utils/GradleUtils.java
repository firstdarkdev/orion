/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.plugin.OrionExtension;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author Hypherionsa
 * Main plugin logic to apply versioning and mavens
 */
public class GradleUtils {

    // Public instance
    public static final GradleUtils INSTANCE = new GradleUtils();

    /**
     * Configure the gradle project, based on the plugin configuration
     * @param target The project the plugin is applied to
     * @param extension The configured gradle extension
     */
    public void configureProject(Project target, OrionExtension extension) {
        // Fetch doppler tokens
        if (target.getRootProject() == target && !extension.getDopplerToken().get().equalsIgnoreCase("INVALID")) {
            DopplerUtils.installDopplerEnvironment(extension.getDopplerToken().get());
        }

        // Register the cleanup logic for multi-platform projects
        registerCleanup(target.getRootProject());

        target.allprojects(p -> {
            // Set the group and version on all projects
            p.setGroup(p.getRootProject().getGroup());
            p.setVersion(extension.getVersioning().buildVersion());

            // Configure the artifact copying logic for multi-platform projects
            if (extension.getMultiProject().get())
                registerCopyLogic(p);

            // Add our releases maven
            if (extension.getEnableReleasesMaven().get()) {
                p.getRepositories().maven(m -> {
                    m.setName("First Dark Dev Maven");
                    m.setUrl(Constants.MAVEN_URL);
                });
            }

            // Add our snapshot maven
            if (extension.getEnableSnapshotsMaven().get()) {
                p.getRepositories().maven(m -> {
                    m.setName("First Dark Dev Snapshots Maven");
                    m.setUrl(Constants.MAVEN_SNAPSHOT_URL);
                });
            }

            // Add our mirror maven
            if (extension.getEnableMirrorMaven().get()) {
                p.getRepositories().maven(m -> {
                    m.setName("First Dark Dev Mirror");
                    m.setUrl(Constants.MAVEN_CENTRAL_URL);
                });
            }

            applyTools(extension, p);
        });
    }

    private void applyTools(OrionExtension extension, Project p) {
        if (extension.getMultiProject().get() && !p.getName().equalsIgnoreCase(p.getRootProject().getName()))
            return;

        if (extension.getTools().isEnableAutoService()) {
            p.getDependencies().add("compileOnly", Constants.AUTO_SERVICE);
            p.getDependencies().add("annotationProcessor", Constants.AUTO_SERVICE);
        }

        if (extension.getTools().isEnableLombok()) {
            p.getDependencies().add("compileOnly", Constants.LOMBOK);
            p.getDependencies().add("annotationProcessor", Constants.LOMBOK);
        }

        if (extension.getTools().isEnableNoLoader()) {
            p.getDependencies().add("compileOnly", Constants.NO_LOADER);
        }
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

        project.afterEvaluate(cc -> {
            project.getTasks().getByName("build").doLast(c -> {
                File artifactDir = new File(project.getRootProject().getRootDir(), "artifacts");
                File libsDir = new File(project.getBuildDir(), "libs");
                File[] files = libsDir.listFiles();

                if (files == null)
                    return;

                for (File f : Arrays.stream(files).filter(f -> !f.isDirectory()).collect(Collectors.toList())) {
                    if (f.getName().contains("-dev-shadow") || f.getName().contains("-dev") || f.getName().contains("-all") || f.getName().contains("-slim")) {
                        f.delete();
                        continue;
                    }

                    if (!artifactDir.exists())
                        artifactDir.mkdirs();

                    try {
                        Files.move(f.toPath(), new File(artifactDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        project.getLogger().error("Failed to copy artifact to output directory", e);
                        throw new GradleException(e.getMessage());
                    }
                }
            });
        });
    }

}
