package com.kftc.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class OpenBankingRegisterResponse {
    
    private String name; // 사용자 이름
    private String ci; // 사용자 고유 CI 값 (88byte)
    private LocalDate birthDate; // 생년월일
    private String gender; // 성별 (M/F)
    private String phoneNumber; // 휴대폰번호
    private String email; // 이메일
} 