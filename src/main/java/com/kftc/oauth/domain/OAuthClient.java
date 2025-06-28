package com.kftc.oauth.domain;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "oauth_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthClient extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;
    
    @Column(name = "client_secret", nullable = false)
    private String clientSecret;
    
    @Column(name = "client_name", nullable = false)
    private String clientName;
    
    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;
    
    @Column(name = "scope")
    private String scope;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(name = "client_use_code")
    private String clientUseCode;
    
    @Builder
    public OAuthClient(String clientId, String clientSecret, String clientName, 
                      String redirectUri, String scope, Boolean isActive, String clientUseCode) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientName = clientName;
        this.redirectUri = redirectUri;
        this.scope = scope;
        this.isActive = isActive != null ? isActive : true;
        this.clientUseCode = clientUseCode;
    }
} 