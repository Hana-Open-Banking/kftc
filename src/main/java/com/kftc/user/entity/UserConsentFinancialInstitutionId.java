package com.kftc.user.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class UserConsentFinancialInstitutionId implements Serializable {
    private String userSeqNo;
    private String bankCodeStd;
    
    public UserConsentFinancialInstitutionId(String userSeqNo, String bankCodeStd) {
        this.userSeqNo = userSeqNo;
        this.bankCodeStd = bankCodeStd;
    }
} 