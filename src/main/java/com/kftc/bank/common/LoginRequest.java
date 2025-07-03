package com.kftc.bank.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    private String bankCode;    // 은행 코드
    private String userId;      // 사용자 ID
    private String password;    // 비밀번호
    private String clientId;    // 클라이언트 ID (선택사항)
} 