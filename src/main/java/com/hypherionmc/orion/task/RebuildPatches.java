package com.hypherionmc.orion.task;

import com.hypherionmc.orion.Constants;
import com.hypherionmc.orion.plugin.porting.OrionPortingExtension;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class RebuildPatches extends DefaultTask {

    @TaskAction
    public void rebuildPatches() throws Exception {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);

        if (extension == null)
            throw new GradleException("Cannot find orionporting extension on project");

        getLogger().lifecycle("Cleaning Patches Directory");
        getProject().delete(new File(getProject().getRootProject().getRootDir(), "patches"));

        TaskActions.INSTANCE.cleanWorkspace(getLogger(), getProject());
        TaskActions.INSTANCE.updateCommitSha(getProject(), getLogger(), extension);
        TaskActions.INSTANCE.setupWorkspace(getProject(), getLogger(), extension);

        for (String b : extension.getPortingBranches().get()) {
            File f = new File(getProject().getRootProject().getRootDir(), b);
            File out = Constants.patcherWorkdir.resolve(b).toFile();

            if (out.exists())
                FileUtils.deleteQuietly(out);

            getLogger().lifecycle("Copying {} into Workspace Directory", b);
            FileUtils.copyDirectory(f, out);
        }

        TaskActions.INSTANCE.generatePatches(getProject(), getLogger(), extension);
        TaskActions.INSTANCE.cleanWorkspace(getLogger(), getProject());
    }

}
