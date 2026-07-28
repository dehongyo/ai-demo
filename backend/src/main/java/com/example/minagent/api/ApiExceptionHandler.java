package com.example.minagent.api;

import com.example.minagent.api.dto.ErrorResponse;
import com.example.minagent.llm.AuthException;
import com.example.minagent.llm.LlmServiceException;
import com.example.minagent.session.SessionBusyException;
import com.example.minagent.session.SessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SessionNotFoundException e, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SESSION_NOT_FOUND", e.getMessage(), requestId()));
    }

    @ExceptionHandler(SessionBusyException.class)
    public ResponseEntity<ErrorResponse> handleBusy(SessionBusyException e, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SESSION_BUSY", e.getMessage(), requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", message, requestId()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException e, WebRequest request) {
        log.error("LLM auth error", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("LLM_AUTH_ERROR",
                        "模型认证失败，请检查 API Key 和 Base URL 配置。", requestId()));
    }

    @ExceptionHandler(LlmServiceException.class)
    public ResponseEntity<ErrorResponse> handleLlmError(LlmServiceException e, WebRequest request) {
        log.error("LLM service error", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("LLM_SERVICE_ERROR",
                        "模型服务暂时不可用，请稍后重试。", requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "服务器内部错误，请稍后重试。", requestId()));
    }

    private String requestId() {
        return UUID.randomUUID().toString();
    }
}
