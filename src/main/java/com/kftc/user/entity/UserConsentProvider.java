package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_consent_provider")
@IdClass(UserConsentProviderId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsentProvider extends DateTimeEntity {
    
    @Id
    @Column(name = "user_seq_no", length = 10)
    private String userSeqNo; // 사용자일련번호
    
    @Id
    @Column(name = "org_code", length = 10)
    private String orgCode; // 이용기관코드
    
    @Column(name = "consent_yn", length = 1)
    private String consentYn; // 이용동의(Y/N)
    
    @Column(name = "consent_dtime", length = 14)
    private String consentDtime; // 동의일시
    
    @Column(name = "upd_dtime", length = 14)
    private String updDtime; // 수정일시
    
    @Builder
    public UserConsentProvider(String userSeqNo, String orgCode, String consentYn, 
                              String consentDtime, String updDtime) {
        this.userSeqNo = userSeqNo;
        this.orgCode = orgCode;
        this.consentYn = consentYn;
        this.consentDtime = consentDtime;
        this.updDtime = updDtime;
    }
    
    public void updateConsent(String consentYn, String updDtime) {
        this.consentYn = consentYn;
        this.updDtime = updDtime;
    }
} 