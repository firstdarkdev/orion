/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author HypherionSA
 * Constant values used throughout the plugin
 */
public class Constants {

    // Strings
    public static final String TASK_GROUP = "orion";
    public static final String PATCH_GROUP = "patches";

    // Tool Deps
    public static final String AUTO_SERVICE = "com.google.auto.service:auto-service:1.1.1";
    public static final String LOMBOK = "org.projectlombok:lombok:1.18.34";
    public static final String NO_LOADER = "com.hypherionmc.noloaderthanks:noloaderthanks:1.0.6";

    // FDD Mavens
    public static final String MAVEN_URL = "https://maven.firstdark.dev/releases";
    public static final String MAVEN_SNAPSHOT_URL = "https://maven.firstdark.dev/snapshots";
    public static final String MAVEN_CENTRAL_URL = "https://mcentral.firstdark.dev/releases";

    // Porting Patcher
    public static final Path patcherUpstream = Paths.get("upstream");
    public static final Path patcherWorkdir = Paths.get("workspace");
    public static final File patcherCommit = new File("commit.sha");
}
