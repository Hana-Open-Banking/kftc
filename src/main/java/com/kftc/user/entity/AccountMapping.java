package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kftc_account_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountMapping extends DateTimeEntity {
    
    @Id
    @Column(name = "fintech_use_num", length = 30)
    private String fintechUseNum; // 핀테크이용번호
    
    @Column(name = "user_seq_no", length = 10, nullable = false)
    private String userSeqNo; // 사용자일련번호
    
    @Column(name = "org_code", length = 10, nullable = false)
    private String orgCode; // 이용기관코드
    
    @Column(name = "bank_code_std", length = 3, nullable = false)
    private String bankCodeStd; // 금융기관표준코드
    
    @Column(name = "account_num", length = 16)
    private String accountNum; // 실제 계좌번호
    
    @Column(name = "account_num_masked", length = 64)
    private String accountNumMasked; // 계좌번호마스킹
    
    @Column(name = "account_alias", length = 50)
    private String accountAlias; // 계좌별명
    
    @Column(name = "account_seq", length = 3)
    private String accountSeq; // 회차번호
    
    @Column(name = "account_holder_name", length = 20)
    private String accountHolderName; // 예금주명
    
    @Column(name = "account_type", length = 1)
    private String accountType; // 계좌구분(P/B)
    
    @Column(name = "inquiry_agree_yn", length = 1)
    private String inquiryAgreeYn; // 조회동의여부
    
    @Column(name = "transfer_agree_yn", length = 1)
    private String transferAgreeYn; // 이체동의여부
    
    @Column(name = "reg_state", length = 20)
    private String regState; // 등록상태
    
    @Column(name = "payer_num", length = 30)
    private String payerNum; // 출금계좌번호
    
    @Column(name = "bank_name", length = 50)
    private String bankName; // 금융기관명
    
    @Column(name = "savings_bank_name", length = 50)
    private String savingsBankName; // 저축은행명
    
    @Column(name = "inquiry_agree_dtime", length = 14)
    private String inquiryAgreeDtime; // 조회동의일시
    
    @Column(name = "transfer_agree_dtime", length = 14)
    private String transferAgreeDtime; // 이체동의일시
    
    @Builder
    public AccountMapping(String fintechUseNum, String userSeqNo, String orgCode, String bankCodeStd,
                         String accountNum, String accountNumMasked, String accountAlias, String accountSeq,
                         String accountHolderName, String accountType, String inquiryAgreeYn,
                         String transferAgreeYn, String regState, String payerNum, String bankName,
                         String savingsBankName, String inquiryAgreeDtime, String transferAgreeDtime) {
        this.fintechUseNum = fintechUseNum;
        this.userSeqNo = userSeqNo;
        this.orgCode = orgCode;
        this.bankCodeStd = bankCodeStd;
        this.accountNum = accountNum;
        this.accountNumMasked = accountNumMasked;
        this.accountAlias = accountAlias;
        this.accountSeq = accountSeq;
        this.accountHolderName = accountHolderName;
        this.accountType = accountType;
        this.inquiryAgreeYn = inquiryAgreeYn;
        this.transferAgreeYn = transferAgreeYn;
        this.regState = regState;
        this.payerNum = payerNum;
        this.bankName = bankName;
        this.savingsBankName = savingsBankName;
        this.inquiryAgreeDtime = inquiryAgreeDtime;
        this.transferAgreeDtime = transferAgreeDtime;
    }
    
    public void updateAccountInfo(String accountNum, String accountAlias, String inquiryAgreeYn, String transferAgreeYn) {
        this.accountNum = accountNum;
        this.accountAlias = accountAlias;
        this.inquiryAgreeYn = inquiryAgreeYn;
        this.transferAgreeYn = transferAgreeYn;
    }
    
    public void updateRegState(String regState) {
        this.regState = regState;
    }
} 