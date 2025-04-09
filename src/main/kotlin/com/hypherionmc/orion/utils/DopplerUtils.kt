/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.GradleException

object DopplerUtils {

    /**
     * Pull secrets from Doppler and expose them as ENV variables, usable with System.getenv()
     * @param token The Doppler access token
     */
    @Suppress("Unchecked_cast")
    fun installDopplerEnvironment(token: String) {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.doppler.com/v3/configs/config/secrets/download?format=json")
            .get()
            .addHeader("accept", "application/json")
            .addHeader("authorization", "Bearer $token")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val values: java.util.HashMap<*, *>? = Gson().fromJson(
                        response.body!!.string(),
                        HashMap::class.java
                    )

                    // No secrets found. Nothing to do
                    if (values == null || values.isEmpty()) return

                    Environment.setVariables(values as HashMap<String, String>)
                }
            }
        } catch (e: Exception) {
            throw GradleException(e.message ?: "Error while installing Doppler environment", e)
        }
    }

}