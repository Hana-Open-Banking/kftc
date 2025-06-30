package com.kftc.user.repository;

import com.kftc.user.entity.FinancialInstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialInstitutionRepository extends JpaRepository<FinancialInstitution, String> {
    
    Optional<FinancialInstitution> findByBankCodeStd(String bankCodeStd);
    
    List<FinancialInstitution> findByBankType(String bankType);
    
    List<FinancialInstitution> findByAccessState(String accessState);
    
    List<FinancialInstitution> findByBankNameContaining(String bankName);
    
    boolean existsByBankCodeStd(String bankCodeStd);
} 