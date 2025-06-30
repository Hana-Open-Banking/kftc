package com.kftc.user.repository;

import com.kftc.user.entity.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, String> {
    
    List<AccountMapping> findByUserSeqNo(String userSeqNo);
    
    List<AccountMapping> findByOrgCode(String orgCode);
    
    List<AccountMapping> findByBankCodeStd(String bankCodeStd);
    
    Optional<AccountMapping> findByFintechUseNum(String fintechUseNum);
    
    List<AccountMapping> findByUserSeqNoAndOrgCode(String userSeqNo, String orgCode);
    
    List<AccountMapping> findByUserSeqNoAndBankCodeStd(String userSeqNo, String bankCodeStd);
    
    boolean existsByFintechUseNum(String fintechUseNum);
} 