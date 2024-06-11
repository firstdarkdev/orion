package com.hypherionmc.orion.plugin.porting;

import com.hypherionmc.orion.task.*;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * @author HypherionSA
 * Main porting plugin entrypoint
 */
public class OrionPortingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        // Register the porting extension and tasks
        target.getRootProject().getExtensions().create("orionporting", OrionPortingExtension.class, target.getRootProject());

        final TaskProvider<SetupWorkspace> setupWorkspaceTask = target.getRootProject().getTasks().register("setupWorkspace", SetupWorkspace.class);
        setupWorkspaceTask.configure(c -> c.setGroup("orion"));

        final TaskProvider<GeneratePatches> genPatchesTask = target.getRootProject().getTasks().register("generatePatches", GeneratePatches.class);
        genPatchesTask.configure(c -> c.setGroup("orion"));

        final TaskProvider<CleanWorkspace> cleanWorkspace = target.getRootProject().getTasks().register("cleanWorkspace", CleanWorkspace.class);
        cleanWorkspace.configure(c -> c.setGroup("orion"));

        final TaskProvider<UpdateCommitSha> updateCommitRef = target.getRootProject().getTasks().register("updateCommitRef", UpdateCommitSha.class);
        updateCommitRef.configure(c -> c.setGroup("orion"));

        final TaskProvider<SplitSources> splitSources = target.getRootProject().getTasks().register("splitSources", SplitSources.class);
        splitSources.configure(c -> c.setGroup("orion"));
    }
}
