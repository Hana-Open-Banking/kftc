package com.kftc.oauth.repository;

import com.kftc.oauth.domain.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, Long> {
    
    Optional<OAuthToken> findByAccessToken(String accessToken);
    
    Optional<OAuthToken> findByRefreshToken(String refreshToken);
    
    Optional<OAuthToken> findByAccessTokenAndIsRevokedFalse(String accessToken);
    
    Optional<OAuthToken> findByRefreshTokenAndIsRevokedFalse(String refreshToken);
    
    List<OAuthToken> findByClientIdAndIsRevokedFalse(String clientId);
    
    List<OAuthToken> findByUserIdAndIsRevokedFalse(String userId);
    
    void deleteByClientIdAndUserId(String clientId, String userId);
} 