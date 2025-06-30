package com.kftc.oauth.dto;

import lombok.Data;

@Data
public class TokenRequest {
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String code;
    private String redirectUri;
    private String refreshToken;
    private String scope;
    private String userId;
} 