package com.devcollab.knowledgecore.common.api;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCredentialsException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidCsrfTokenException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidRefreshTokenException;
import com.devcollab.knowledgecore.auth.application.exception.UsernameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("请求参数不合法");

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "AUTH_INVALID_INPUT",
                message,
                request
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUsernameAlreadyExistsException(
            UsernameAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "AUTH_USERNAME_EXISTS",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshTokenException(
            InvalidRefreshTokenException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REFRESH_INVALID",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidCsrfTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCsrfTokenException(
            InvalidCsrfTokenException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.FORBIDDEN,
                "AUTH_CSRF_INVALID",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "AUTH_INVALID_INPUT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "服务器内部错误",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = ApiErrorResponse.of(
                status.value(),
                code,
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}
