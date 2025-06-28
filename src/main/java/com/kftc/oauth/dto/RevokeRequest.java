package com.kftc.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "토큰 폐기 요청")
public class RevokeRequest {
    
    @Schema(description = "오픈뱅킹에서 발급한 이용기관 연동 Client ID", required = true, example = "kftc-openbanking-client")
    @JsonProperty("client_id")
    private String clientId;
    
    @Schema(description = "오픈뱅킹에서 발급한 이용기관 연동 Client Secret", required = true, example = "kftc-openbanking-secret")
    @JsonProperty("client_secret")
    private String clientSecret;
    
    @Schema(description = "폐기하고자 하는 Access Token", required = true, example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("access_token")
    private String accessToken;
} 