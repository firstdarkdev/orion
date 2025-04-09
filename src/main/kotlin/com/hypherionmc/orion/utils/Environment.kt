/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

object Environment {

    val variables: HashMap<String, String> = hashMapOf()

    internal fun setVariables(variables: HashMap<String, String>) {
        this.variables.clear()
        this.variables.putAll(variables)
    }

    @JvmStatic
    fun getenv(key: String): String? {
        return variables.getOrDefault(key, System.getenv(key))
    }
}