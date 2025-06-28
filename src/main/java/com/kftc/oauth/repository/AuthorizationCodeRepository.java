package com.kftc.oauth.repository;

import com.kftc.oauth.domain.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {
    
    Optional<AuthorizationCode> findByCode(String code);
    
    Optional<AuthorizationCode> findByCodeAndIsUsedFalse(String code);
    
    void deleteByCode(String code);
    
    void deleteByClientIdAndUserId(String clientId, String userId);
} 