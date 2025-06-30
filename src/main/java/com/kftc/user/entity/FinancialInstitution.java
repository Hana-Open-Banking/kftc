package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_institution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialInstitution extends DateTimeEntity {
    
    @Id
    @Column(name = "bank_code_std", length = 3)
    private String bankCodeStd; // 금융기관표준코드
    
    @Column(name = "bank_name", length = 20, nullable = false)
    private String bankName; // 금융기관명
    
    @Column(name = "bank_type", length = 20, nullable = false)
    private String bankType; // 기관유형(BANK/CARD/INSURANCE/LOAN)
    
    @Column(name = "access_state", length = 20)
    private String accessState; // 접근상태
    
    @Builder
    public FinancialInstitution(String bankCodeStd, String bankName, String bankType, String accessState) {
        this.bankCodeStd = bankCodeStd;
        this.bankName = bankName;
        this.bankType = bankType;
        this.accessState = accessState;
    }
    
    public void updateAccessState(String accessState) {
        this.accessState = accessState;
    }
    
    public boolean isAccessible() {
        return "ACTIVE".equals(this.accessState);
    }
} 