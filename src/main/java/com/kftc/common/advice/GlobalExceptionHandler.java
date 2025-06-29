package com.kftc.common.advice;

import com.kftc.common.dto.BasicResponse;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<BasicResponse> handleException(Exception e) {
        log.error("Unexpected error occurred", e);
        BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("서버에 문제가 발생하였습니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<BasicResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        BasicResponse response = BasicResponse.builder()
                .status(errorCode.getStatus())
                .message(e.getMessage())
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<BasicResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("적절하지 않은 요청 값입니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<BasicResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("요청 값의 타입이 잘못되었습니다.")
                .data(null)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<BasicResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
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

}
