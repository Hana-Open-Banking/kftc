package com.kftc.user.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class UserConsentProviderId implements Serializable {
    private String userSeqNo;
    private String orgCode;
    
    public UserConsentProviderId(String userSeqNo, String orgCode) {
        this.userSeqNo = userSeqNo;
        this.orgCode = orgCode;
    }
} 