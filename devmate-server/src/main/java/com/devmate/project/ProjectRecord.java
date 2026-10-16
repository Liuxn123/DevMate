package com.devmate.project;

/** A project entry persisted by DevMate. This slice registers paths without inspecting them. */
public record ProjectRecord(
        String id,
        String name,
        String sourceType,
        String repositoryUrl,
        String rootPath,
        String projectType,
        String createdAt) {
}
