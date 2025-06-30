package com.kftc.user.repository;

import com.kftc.user.entity.UserConsentProvider;
import com.kftc.user.entity.UserConsentProviderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConsentProviderRepository extends JpaRepository<UserConsentProvider, UserConsentProviderId> {
    
    List<UserConsentProvider> findByUserSeqNo(String userSeqNo);
    
    List<UserConsentProvider> findByOrgCode(String orgCode);
    
    Optional<UserConsentProvider> findByUserSeqNoAndOrgCode(String userSeqNo, String orgCode);
    
    List<UserConsentProvider> findByUserSeqNoAndConsentYn(String userSeqNo, String consentYn);
    
    boolean existsByUserSeqNoAndOrgCode(String userSeqNo, String orgCode);
} 