package com.kftc.bank.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 은행 API 공통 응답 구조
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonBankResponse<T> {
    private String message;
    private Boolean success;
    private String errorCode;
    private T data;
    
    public static <T> CommonBankResponse<T> success(String message, T data) {
        return CommonBankResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    public static <T> CommonBankResponse<T> error(String errorCode, String message) {
        return CommonBankResponse.<T>builder()
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .build();
    }
} 