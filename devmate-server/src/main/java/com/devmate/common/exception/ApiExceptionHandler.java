package com.devmate.common.exception;

import com.devmate.agent.exception.AgentModelNotConfiguredException;
import com.devmate.agent.exception.AgentProviderException;
import com.devmate.common.dto.ApiError;
import com.devmate.project.exception.InvalidProjectPathException;
import com.devmate.project.exception.ProjectAlreadyRegisteredException;
import com.devmate.project.exception.ProjectNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(
            org.springframework.web.server.ResponseStatusException exception, HttpServletRequest request) {
        return error(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .orElse("请求参数无效");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(InvalidProjectPathException.class)
    public ResponseEntity<ApiError> handleInvalidPath(
            InvalidProjectPathException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ProjectAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleDuplicateProject(
            ProjectAlreadyRegisteredException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingProject(
            ProjectNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(AgentModelNotConfiguredException.class)
    public ResponseEntity<ApiError> handleUnconfiguredAgent(
            AgentModelNotConfiguredException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(AgentProviderException.class)
    public ResponseEntity<ApiError> handleAgentProviderFailure(
            AgentProviderException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(
            IllegalArgumentException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                java.time.Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
