package com.devmate.task.model;

public record TaskRecord(String id, int version, String contentHash, ExecutionPlan plan,
        String status, String createdAt, String updatedAt, String approvedAt,
        Integer exitCode, String result) {
}
