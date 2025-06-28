package com.kftc.oauth.domain;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "authorization_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorizationCode extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", unique = true, nullable = false)
    private String code;
    
    @Column(name = "client_id", nullable = false)
    private String clientId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;
    
    @Column(name = "scope")
    private String scope;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "is_used")
    private Boolean isUsed;
    
    @Builder
    public AuthorizationCode(String code, String clientId, String userId, 
                           String redirectUri, String scope, LocalDateTime expiresAt) {
        this.code = code;
        this.clientId = clientId;
        this.userId = userId;
        this.redirectUri = redirectUri;
        this.scope = scope;
        this.expiresAt = expiresAt;
        this.isUsed = false;
    }
    
    public void markAsUsed() {
        this.isUsed = true;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
} 