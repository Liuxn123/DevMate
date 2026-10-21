package com.devmate.project.dto;

import java.util.List;

public record PomCandidatesResponse(List<String> pomPaths, boolean truncated, String notice) {
}
