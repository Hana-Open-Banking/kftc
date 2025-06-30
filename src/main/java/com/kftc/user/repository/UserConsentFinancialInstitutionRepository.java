package com.kftc.user.repository;

import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.entity.UserConsentFinancialInstitutionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConsentFinancialInstitutionRepository extends JpaRepository<UserConsentFinancialInstitution, UserConsentFinancialInstitutionId> {
    
    List<UserConsentFinancialInstitution> findByUserSeqNo(String userSeqNo);
    
    List<UserConsentFinancialInstitution> findByBankCodeStd(String bankCodeStd);
    
    Optional<UserConsentFinancialInstitution> findByUserSeqNoAndBankCodeStd(String userSeqNo, String bankCodeStd);
    
    List<UserConsentFinancialInstitution> findByUserSeqNoAndRegStatus(String userSeqNo, String regStatus);
    
    boolean existsByUserSeqNoAndBankCodeStd(String userSeqNo, String bankCodeStd);
} 