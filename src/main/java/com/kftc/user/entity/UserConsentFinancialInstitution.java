package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_consent_financial_institution")
@IdClass(UserConsentFinancialInstitutionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsentFinancialInstitution extends DateTimeEntity {
    
    @Id
    @Column(name = "user_seq_no", length = 10)
    private String userSeqNo; // 사용자일련번호
    
    @Id
    @Column(name = "bank_code_std", length = 3)
    private String bankCodeStd; // 금융기관표준코드
    
    @Column(name = "info_prvd_agmt_yn", length = 1)
    private String infoPrvdAgmtYn; // 정보제공동의(Y/N)
    
    @Column(name = "info_prvd_agmt_dtime", length = 14)
    private String infoPrvdAgmtDtime; // 동의일시
    
    @Column(name = "account_inquiry_yn", length = 1)
    private String accountInquiryYn; // 계좌조회동의(Y/N)
    
    @Column(name = "transaction_inquiry_yn", length = 1)
    private String transactionInquiryYn; // 거래내역조회동의(Y/N)
    
    @Column(name = "balance_inquiry_yn", length = 1)
    private String balanceInquiryYn; // 잔액조회동의(Y/N)
    
    @Column(name = "reg_status", length = 20)
    private String regStatus; // 등록상태(ACTIVE/INACTIVE)
    
    @Column(name = "reg_dtime", length = 14)
    private String regDtime; // 등록일시
    
    @Column(name = "upd_dtime", length = 14)
    private String updDtime; // 수정일시
    
    @Builder
    public UserConsentFinancialInstitution(String userSeqNo, String bankCodeStd, String infoPrvdAgmtYn,
                                         String infoPrvdAgmtDtime, String accountInquiryYn, String transactionInquiryYn,
                                         String balanceInquiryYn, String regStatus, String regDtime, String updDtime) {
        this.userSeqNo = userSeqNo;
        this.bankCodeStd = bankCodeStd;
        this.infoPrvdAgmtYn = infoPrvdAgmtYn;
        this.infoPrvdAgmtDtime = infoPrvdAgmtDtime;
        this.accountInquiryYn = accountInquiryYn;
        this.transactionInquiryYn = transactionInquiryYn;
        this.balanceInquiryYn = balanceInquiryYn;
        this.regStatus = regStatus;
        this.regDtime = regDtime;
        this.updDtime = updDtime;
    }
    
    public void updateConsent(String infoPrvdAgmtYn, String accountInquiryYn, 
                             String transactionInquiryYn, String balanceInquiryYn, String updDtime) {
        this.infoPrvdAgmtYn = infoPrvdAgmtYn;
        this.accountInquiryYn = accountInquiryYn;
        this.transactionInquiryYn = transactionInquiryYn;
        this.balanceInquiryYn = balanceInquiryYn;
        this.updDtime = updDtime;
    }
    
    public void updateStatus(String regStatus, String updDtime) {
        this.regStatus = regStatus;
        this.updDtime = updDtime;
    }
} 