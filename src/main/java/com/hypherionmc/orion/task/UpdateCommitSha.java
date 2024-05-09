package com.hypherionmc.orion.task;

import com.hypherionmc.orion.plugin.OrionPortingExtension;
import com.hypherionmc.orion.utils.Patcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

public class UpdateCommitSha extends DefaultTask {

    @TaskAction
    public void updateCommitSha() throws Exception {
        OrionPortingExtension extension = getProject().getExtensions().findByType(OrionPortingExtension.class);
        if (extension == null)
            throw new GradleException("orionporting extension is not configured");

        if (!extension.getUpstreamBranch().isPresent() || extension.getUpstreamBranch().get().equalsIgnoreCase("INVALID")) {
            throw new GradleException("No upstream branch specified.");
        }

        Patcher.INSTANCE.checkoutUpstreamBranch(getProject(), extension.getUpstreamBranch().get(), null, false);
    }

}
