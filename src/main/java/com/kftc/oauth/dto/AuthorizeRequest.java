package com.kftc.oauth.dto;

import lombok.Data;

@Data
public class AuthorizeRequest {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;
    private String userId;
} 