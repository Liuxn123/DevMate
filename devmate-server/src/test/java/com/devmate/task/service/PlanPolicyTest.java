package com.devmate.task.service;

import com.devmate.project.dto.ProjectEnvironment;
import com.devmate.project.model.ProjectRecord;
import com.devmate.task.dto.PlanRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanPolicyTest {
    @TempDir Path directory;

    @Test void rejectsTraversalAndAbsolutePaths() throws Exception {
        Path root = Files.createDirectory(directory.resolve("project"));
        Path external = Files.writeString(directory.resolve("outside.xml"), "outside");
        assertThrows(IllegalArgumentException.class, () -> PlanPolicy.containedFile(root, "../outside.xml"));
        assertThrows(IllegalArgumentException.class, () -> PlanPolicy.containedFile(root, external.toString()));
    }

    @Test void rejectsModelInventedAction() {
        assertThrows(IllegalArgumentException.class, () -> new PlanPolicy().create(null, null, null, "RUN_SHELL"));
    }

    @Test void buildsArgumentArrayAndDetectsChangedPom() throws Exception {
        Path root = Files.createDirectory(directory.resolve("project with spaces"));
        Path pom = Files.writeString(root.resolve("pom.xml"), "<project/>");
        Path javaHome = Files.createDirectory(directory.resolve("jdk"));
        Files.createDirectories(javaHome.resolve("bin"));
        Files.writeString(javaHome.resolve("bin/java.exe"), "fake launcher; never executed");
        Path maven = Files.createDirectory(directory.resolve("maven"));
        Files.createDirectories(maven.resolve("boot"));
        Files.createDirectories(maven.resolve("bin"));
        Files.writeString(maven.resolve("boot/plexus-classworlds-2.7.0.jar"), "fixture");
        Files.writeString(maven.resolve("bin/m2.conf"), "fixture");
        var project = new ProjectRecord("id", "test", "LOCAL", null, root.toString(), "MAVEN_JAVA", "now");
        var settings = new ProjectEnvironment(javaHome.toString(), maven.toString(), directory.resolve("cache").toString());
        var request = new PlanRequest("id", "pom.xml", "build", "MAVEN_PACKAGE", null, false);
        PlanPolicy policy = new PlanPolicy();
        var plan = policy.create(project, settings, request, "MAVEN_PACKAGE");
        assertTrue(plan.command().contains(pom.toRealPath().toString()));
        assertEquals("package", plan.command().getLast());
        assertFalse(plan.command().contains("cmd.exe"));
        assertDoesNotThrow(() -> policy.verify(plan));
        Files.writeString(pom, "<project>changed</project>");
        assertThrows(IllegalArgumentException.class, () -> policy.verify(plan));
    }
}
