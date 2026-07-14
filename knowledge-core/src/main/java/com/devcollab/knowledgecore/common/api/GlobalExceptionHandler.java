package com.devcollab.knowledgecore.common.api;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCredentialsException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidCsrfTokenException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidRefreshTokenException;
import com.devcollab.knowledgecore.auth.application.exception.UsernameAlreadyExistsException;
import com.devcollab.knowledgecore.document.application.exception.DocumentNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentBlockNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.DocumentBlockVersionConflictException;
import com.devcollab.knowledgecore.document.application.exception.DocumentParentCycleException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentParentException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentBlockPositionException;
import com.devcollab.knowledgecore.document.application.exception.InvalidDocumentReviewStatusException;
import com.devcollab.knowledgecore.document.application.exception.DocumentVersionNotFoundException;
import com.devcollab.knowledgecore.document.application.exception.ReviewIssueNotFoundException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceAccessDeniedException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceLastAdminException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceMemberAlreadyExistsException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceMemberNotFoundException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceNotFoundException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceUserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
                inputErrorCode(request),
                message,
                request
        );
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceNotFound(
            WorkspaceNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceAccessDenied(
            WorkspaceAccessDeniedException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.FORBIDDEN,
                "WORKSPACE_ACCESS_DENIED",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(WorkspaceUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceUserNotFound(
            WorkspaceUserNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_USER_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceMemberNotFound(
            WorkspaceMemberNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "WORKSPACE_MEMBER_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(WorkspaceMemberAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceMemberAlreadyExists(
            WorkspaceMemberAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "WORKSPACE_MEMBER_EXISTS",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(WorkspaceLastAdminException.class)
    public ResponseEntity<ApiErrorResponse> handleWorkspaceLastAdmin(
            WorkspaceLastAdminException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "WORKSPACE_LAST_ADMIN",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentNotFound(
            DocumentNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "DOCUMENT_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentBlockNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentBlockNotFound(
            DocumentBlockNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "DOCUMENT_BLOCK_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentBlockVersionConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentBlockVersionConflict(
            DocumentBlockVersionConflictException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "DOCUMENT_BLOCK_VERSION_CONFLICT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidDocumentParentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDocumentParent(
            InvalidDocumentParentException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_PARENT_INVALID",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentParentCycleException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentParentCycle(
            DocumentParentCycleException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_PARENT_CYCLE",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidDocumentBlockPositionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBlockPosition(
            InvalidDocumentBlockPositionException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_BLOCK_POSITION_INVALID",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidDocumentReviewStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDocumentReviewStatus(
            InvalidDocumentReviewStatusException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "DOCUMENT_REVIEW_STATUS_INVALID",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(DocumentVersionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentVersionNotFound(
            DocumentVersionNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "DOCUMENT_VERSION_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ReviewIssueNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewIssueNotFound(
            ReviewIssueNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "REVIEW_ISSUE_NOT_FOUND",
                exception.getMessage(),
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
                inputErrorCode(request),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                inputErrorCode(request),
                "请求体格式错误或包含不支持的字段值",
                request
        );
    }

    private String inputErrorCode(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/auth/")
                ? "AUTH_INVALID_INPUT"
                : "INVALID_INPUT";
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
