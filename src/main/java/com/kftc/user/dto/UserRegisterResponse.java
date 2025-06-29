package com.kftc.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRegisterResponse {
    
    private Long userId;
    private String name;
    private String phoneNumber;
    private boolean phoneVerified;
    private String ci;  // 사용자 고유 CI 값
    private String userSeqNo;
    private String accessToken;
    private String kftcAuthUrl;  // KFTC 인증 URL
} 