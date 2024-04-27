/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin;

import com.hypherionmc.orion.Constants;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.provider.Property;

/**
 * @author HypherionSA
 * Main Gradle plugin extension. Using this is optional for most part
 */
@Getter
public class OrionExtension {

    // Properties
    private final Versioning versioning = new Versioning();
    private final Property<Boolean> enableReleasesMaven;
    private final Property<Boolean> enableSnapshotsMaven;
    private final Property<Boolean> enableMirrorMaven;
    private final Property<Boolean> multiProject;
    private final Property<String> dopplerToken;

    // Project Reference
    private final Project project;

    public OrionExtension(Project project) {
        this.project = project;

        // Try to configure versioning from gradle properties
        if (project.hasProperty("version_major"))
            versioning.major(Integer.parseInt(project.getProperties().get("version_major").toString()));

        if (project.hasProperty("version_minor"))
            versioning.minor(Integer.parseInt(project.getProperties().get("version_minor").toString()));

        if (project.hasProperty("version_patch"))
            versioning.patch(Integer.parseInt(project.getProperties().get("version_patch").toString()));

        if (project.hasProperty("release") && project.getProperties().get("release").toString().equalsIgnoreCase("true"))
            versioning.identifier("release");

        // Set default values for extension
        this.enableReleasesMaven = project.getObjects().property(Boolean.class).convention(false);
        this.enableSnapshotsMaven = project.getObjects().property(Boolean.class).convention(false);
        this.enableMirrorMaven = project.getObjects().property(Boolean.class).convention(false);
        this.multiProject = project.getObjects().property(Boolean.class).convention(false);
        this.dopplerToken = project.getObjects().property(String.class).convention("INVALID");
    }

    /**
     * Configure the version generator for the plugin
     * @param action The DSL of the versioning
     */
    public void versioning(Action<Versioning> action) {
        action.execute(versioning);
    }

    /**
     * Configure Maven for publishing. This defaults to releases, or snapshots for porting/snapshot builds
     * @return The configured maven repository
     */
    public Action<? extends MavenArtifactRepository> getPublishingMaven() {
        return (Action<MavenArtifactRepository>) mavenArtifactRepository -> {
            mavenArtifactRepository.setName("First Dark Dev Maven");
            mavenArtifactRepository.setUrl((versioning.identifier.equalsIgnoreCase("snapshot") || versioning.identifier.equalsIgnoreCase("port")) ? Constants.MAVEN_SNAPSHOT_URL : Constants.MAVEN_URL);

            mavenArtifactRepository.credentials(c -> {
                c.setUsername(System.getenv("MAVEN_USER"));
                c.setPassword(System.getenv("MAVEN_PASS"));
            });
        };
    }

    /**
     * Versioning DSL extension
     */
    @Getter
    public static class Versioning {
        // Default to 1.0.0 release
        private int major = 1;
        private int minor = 0;
        private int patch = 0;
        private String identifier = "release";

        /**
         * Manually configure the MAJOR version value
         * @param major In semver format, for example 1
         */
        public void major(int major) {
            this.major = major;
        }

        /**
         * Manually configure the MINOR version value
         * @param minor In semver format, for example 1
         */
        public void minor(int minor) {
            this.minor = minor;
        }

        /**
         * Manually configure the PATCH version value
         * @param patch In semver format, for example 1
         */
        public void patch(int patch) {
            this.patch = patch;
        }

        /**
         * Set the release identifier. For example, porting, snapshots, etc
         * @param identifier The identifier. Defaults to release, or snapshots when on CI
         */
        public void identifier(String identifier) {
            this.identifier = identifier;
        }

        /**
         * Build a version string to be used inside gradle.
         * This takes into account the CI build number, if one is present
         * @return Semver Version. For example: 1.0.0+port.1
         */
        public String buildVersion() {
            String v = "%s.%s.%s";

            if (System.getenv("BUILD_NUMBER") != null
                    && (identifier.equalsIgnoreCase("port") || identifier.equalsIgnoreCase("snapshot"))) {
                int build = Integer.parseInt(System.getenv("BUILD_NUMBER")) - 1;
                v += "+" + identifier + "." + build;
            }

            return String.format(v, major, minor, patch);
        }
    }
}
