package com.kftc.common.advice;

import com.kftc.common.dto.BasicResponse;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Set;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 무시할 악성/불필요 요청 패턴들
    private static final Set<String> IGNORED_PATHS = Set.of(
            "favicon.ico",
            ".php",
            ".asp",
            ".aspx",
            ".jsp",
            "wp-admin",
            "wp-login",
            "admin",
            "phpmyadmin",
            "mysql",
            ".env",
            "robots.txt",
            "sitemap.xml",
            ".well-known",
            "vendor",
            "config",
            "backup",
            ".git",
            ".svn"
    );

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<BasicResponse> handleBusinessException(BusinessException e) {
        log.error("Business exception occurred: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        BasicResponse response = BasicResponse.builder()
                .status(errorCode.getStatus())
                .message(errorCode.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<BasicResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("Method not allowed error", e);
        BasicResponse response = BasicResponse.builder()
                .status(405)
                .message("잘못된 HTTP Request Method 입니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<BasicResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("Data integrity violation error", e);
        String errorMessage = e.getMessage();
        ErrorCode errorCode;
        // 제약조건 위반 원인 분석
        if (errorMessage != null) {
            if (errorMessage.contains("phone_number") || errorMessage.contains("phoneNumber")) {
                errorCode = ErrorCode.DUPLICATED_PHONE_NUMBER;
            } else if (errorMessage.contains("ci")) {
                errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            } else {
                errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            }
        } else {
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        }
        BasicResponse response = BasicResponse.builder()
                .status(errorCode.getStatus())
                .message(errorCode.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    @ExceptionHandler(IllegalStateException.class)
    protected ResponseEntity<BasicResponse> handleIllegalStateException(IllegalStateException e) {
        log.error("Illegal state error", e);
        BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("서버에 문제가 발생하였습니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ========== 새로 추가된 보안 관련 핸들러들 ==========

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BasicResponse> handleNoHandlerFoundException(NoHandlerFoundException e) {
        String requestUrl = e.getRequestURL();

        // 악성/불필요 요청 패턴 확인
        if (isIgnoredRequest(requestUrl)) {
            log.debug("Ignored suspicious request: {} {}", e.getHttpMethod(), requestUrl);
            return ResponseEntity.notFound().build();
        }

        // 정상적인 404 요청은 로그 남김
        log.warn("No handler found for: {} {}", e.getHttpMethod(), requestUrl);
        BasicResponse response = BasicResponse.builder()
                .status(404)
                .message("요청한 리소스를 찾을 수 없습니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BasicResponse> handleException(Exception e) {
        String message = e.getMessage();

        // 악성/불필요 요청 관련 예외는 조용히 처리
        if (message != null && isIgnoredRequest(message)) {
            log.debug("Ignored suspicious exception: {}", message);
            return ResponseEntity.notFound().build();
        }

        // 정상적인 예외만 에러 로그 남김
        log.error("Unexpected error occurred", e);
        BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("서버 내부 오류가 발생했습니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 무시할 요청인지 확인
     */
    private boolean isIgnoredRequest(String requestPath) {
        if (requestPath == null) {
            return false;
        }

        String lowerPath = requestPath.toLowerCase();
        return IGNORED_PATHS.stream().anyMatch(lowerPath::contains);
    }
}