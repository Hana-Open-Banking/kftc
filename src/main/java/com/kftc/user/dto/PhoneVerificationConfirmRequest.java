package com.kftc.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PhoneVerificationConfirmRequest {
    
    @NotBlank(message = "휴대폰번호는 필수입니다.")
    @Pattern(regexp = "^01[016789]\\d{8}$", message = "올바른 휴대폰번호 형식이 아닙니다.")
    private String phoneNumber;
    
    @NotBlank(message = "인증코드는 필수입니다.")
    @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자여야 합니다.")
    private String verificationCode;
} 