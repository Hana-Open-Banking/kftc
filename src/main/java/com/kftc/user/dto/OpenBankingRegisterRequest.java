package com.kftc.user.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class OpenBankingRegisterRequest {
    
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이내여야 합니다.")
    private String name;
    
    @NotBlank(message = "주민등록번호는 필수입니다.")
    @Pattern(regexp = "^\\d{13}$", message = "주민등록번호는 13자리 숫자여야 합니다.")
    private String socialSecurityNumber;
    
    @NotBlank(message = "휴대폰번호는 필수입니다.")
    @Pattern(regexp = "^01[0-9]\\d{8}$", message = "올바른 휴대폰번호 형식이 아닙니다. (예: 01012345678)")
    private String phoneNumber;
    
    @Size(max = 100, message = "이메일은 100자 이내여야 합니다.")
    private String email; // 선택사항
} 