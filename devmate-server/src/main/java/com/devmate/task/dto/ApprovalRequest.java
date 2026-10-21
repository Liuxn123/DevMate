package com.devmate.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(int version, @NotBlank @Size(max = 64) String contentHash) {
}
