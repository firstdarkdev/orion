package com.hypherionmc.orion.task;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SplitSources extends DefaultTask {

    @TaskAction
    public void splitSources() throws IOException {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);
        if (extension == null)
            throw new GradleException("orionporting extension is not configured");

        if (!Files.exists(Constants.patcherWorkdir))
            throw new GradleException("Working Directory does NOT exist");

        for (String b : extension.getPortingBranches()) {
            File f = new File(getProject().getRootProject().getRootDir(), b);
            if (f.exists())
                FileUtils.deleteQuietly(f);

            FileUtils.copyDirectory(Constants.patcherWorkdir.resolve(b).toFile(), f);
        }
    }

}
