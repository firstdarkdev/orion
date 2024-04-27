/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.gradle.api.GradleException;

import java.util.HashMap;

/**
 * @author HypherionSA
 * Doppler Utilities Class
 */
public class DopplerUtils {

    /**
     * Pull secrets from Doppler and expose them as ENV variables, usable with System.getenv()
     * @param token The Doppler access token
     */
    @SuppressWarnings("unchecked")
    public static void installDopplerEnvironment(String token) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.doppler.com/v3/configs/config/secrets/download?format=json")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                HashMap<String, String> values = new Gson().fromJson(response.body().string(), HashMap.class);

                // No secrets found. Nothing to do
                if (values.isEmpty())
                    return;

                values.forEach((k, v) -> {
                    // Replace any local variables, with their doppler values
                    if (System.getenv().containsKey(k)) {
                        System.getenv().replace(k, v);
                    } else {
                        // Variable doesn't exist, so we add it
                        System.getenv().put(k, v);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new GradleException(e.getMessage());
        }
    }

}
