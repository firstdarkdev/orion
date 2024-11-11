package com.hypherionmc.orion.plugin.paper;

import com.hypherionmc.orion.task.paper.BeforeCompileTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public class OrigamiPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getExtensions().create("origami", OrigamiExtension.class, target);

        final TaskProvider<BeforeCompileTask> preparePluginSources = target.getTasks().register("preparePluginSources", BeforeCompileTask.class);
        target.getTasks().withType(JavaCompile.class).forEach(t -> t.dependsOn(preparePluginSources));
    }
}
