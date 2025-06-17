package com.hypherionmc.orion.task.paper;

import com.hypherionmc.orion.plugin.paper.OrigamiExtension;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeforeCompileTask extends DefaultTask {

    @TaskAction
    public void prepareSourcesTask() throws IOException {
        OrigamiExtension extension = getProject().getExtensions().findByType(OrigamiExtension.class);

        if (extension == null)
            throw new GradleException("Cannot find origami extension on project");

        Project common = getProject().getRootProject().findProject(extension.getCommonProject().get());

        if (common == null) {
            getProject().getLogger().warn("Cannot find Common Project {}", extension.getCommonProject().get());
            return;
        }

        File sourcesFolder = new File(common.getProjectDir(), "src/main");
        if (!sourcesFolder.exists()) {
            getProject().getLogger().warn("Cannot find Sources folder in {}", extension.getCommonProject().get());
            return;
        }

        File destFolder = new File(getProject().getBuildDir(), "commonShared");
        if (destFolder.exists()) {
            FileUtils.deleteDirectory(destFolder);
        }
        destFolder.mkdirs();

        FileUtils.copyDirectory(sourcesFolder, destFolder);

        for (String excludedPackage : extension.getExcludedPackages().get()) {
            File pkg = new File(destFolder, "java/" + excludedPackage.replace(".", "/"));
            if (pkg.exists())
                FileUtils.deleteDirectory(pkg);
        }

        for (String excludedResource : extension.getExcludedResources().get()) {
            File pkg = new File(destFolder, "resources/" + excludedResource);
            if (pkg.exists()) {
                if (pkg.isDirectory()) {
                    FileUtils.deleteDirectory(pkg);
                } else {
                    FileUtils.delete(pkg);
                }
            }
        }

        processComments(destFolder);

        getProject().getTasks().withType(JavaCompile.class).forEach(t -> t.source(new File(destFolder, "java")));
    }

    private void processComments(File sourceDir) {
        try {
            Files.walkFileTree(sourceDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                        stripSpecialCode(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new GradleException(e.getMessage());
        }
    }

    private void stripSpecialCode(File file) {
        try {
            String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

            if (content.contains("// @excludeplugin")) {
                FileUtils.delete(file);
                return;
            }

            String regex = "(?m)(?s)^\\s*// @noplugin.*?// #noplugin\\s*$";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);

            String updatedContent = matcher.replaceAll("\n");
            updatedContent = updatedContent.replaceAll("(?m)^[ \t]*\n{2,}", "\n");

            FileUtils.write(file, updatedContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException(e.getMessage());
        }
    }

}
