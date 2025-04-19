/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

/**
 * @author HypherionSA
 *
 * Expose a virtual environment variables environment, that contain both System and Doppler variables
 */
object Environment {

    // Internal map of Doppler and System environment variables
    private val variables: HashMap<String, String> = hashMapOf()

    /**
     * Internal method to add Doppler tokens to Environment Variables
     *
     * @param variables The Key-Value set of variables to install
     */
    internal fun setVariables(variables: HashMap<String, String>) {
        this.variables.clear()
        this.variables.putAll(variables)
    }

    /**
     * Get an environment variable from either Doppler or the System
     *
     * @param key The key to get the value of
     */
    @JvmStatic
    fun getenv(key: String): String? {
        return variables.getOrDefault(key, System.getenv(key))
    }
}