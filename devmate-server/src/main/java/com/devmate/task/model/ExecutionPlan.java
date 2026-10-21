package com.devmate.task.model;

import java.util.List;
import java.util.Map;

/** Entire approval payload, including the resolved command and its input fingerprints. */
public record ExecutionPlan(
        String projectId, String goal, String action, String pomPath,
        String workingDirectory, List<String> command, Map<String, String> environment,
        Map<String, String> fingerprints, int timeoutSeconds,
        List<String> risks, List<String> verification) {
}
