/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * @author HypherionSA
 * Custom Environment Variables
 */
public class Environment {

    private static final HashMap<String, String> variables = new HashMap<>();

    static void setVariables(HashMap<String, String> variables) {
        Environment.variables.clear();
        Environment.variables.putAll(variables);
    }

    @Nullable
    public static String getenv(String key) {
        return variables.getOrDefault(key, System.getenv(key));
    }

}
