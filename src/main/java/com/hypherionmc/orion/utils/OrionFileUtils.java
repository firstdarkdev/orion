/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @author HypherionSA
 * Helper class for working with files
 */
public class OrionFileUtils {

    /**
     * Move Files between directories, optionally filtering out binary files
     * @param sourceDir The directory to copy from
     * @param destDir The directory to copy to
     * @param filtered Only copy text files
     */
    public static void moveFiles(File sourceDir, File destDir, boolean filtered) {
        try {
            Files.createDirectories(destDir.toPath());

            Files.walk(sourceDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> !filtered || !isBinary(path.toFile()))
                    .forEach(sourceFile -> {
                        Path destFile = destDir.toPath().resolve(sourceDir.toPath().relativize(sourceFile));
                        destFile.toFile().getParentFile().mkdirs();
                        try {
                            Files.move(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            System.err.println("Failed to move: " + sourceFile + " to " + destFile);
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to move files: " + e.getMessage());
        }
    }

    /**
     * Try to determine if a file is a binary or text file
     * @param file - The file to test
     * @return - True if binary
     */
    public static boolean isBinary(@NotNull File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int size = (int) Math.min(file.length(), 4096);
            byte[] data = new byte[size];
            int bytesRead = inputStream.read(data, 0, size);

            for (int i = 0; i < bytesRead; i++) {
                if (data[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
