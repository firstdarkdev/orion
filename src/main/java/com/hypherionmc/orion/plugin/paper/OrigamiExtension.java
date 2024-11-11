package com.hypherionmc.orion.plugin.paper;

import lombok.Getter;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.ArrayList;

@Getter
public class OrigamiExtension {

    private final ListProperty<String> excludedPackages;
    private final ListProperty<String> excludedResources;
    private final Property<String> commonProject;

    private final Project project;

    public OrigamiExtension(Project project) {
        this.project = project;

        this.excludedPackages = project.getObjects().listProperty(String.class).convention(new ArrayList<>());
        this.excludedResources = project.getObjects().listProperty(String.class).convention(new ArrayList<>());
        this.commonProject = project.getObjects().property(String.class).convention("Common");
    }

}
