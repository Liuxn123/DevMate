package com.devmate.common;

import com.devmate.project.InvalidProjectPathException;
import com.devmate.project.ProjectAlreadyRegisteredException;
import com.devmate.project.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                java.time.Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
