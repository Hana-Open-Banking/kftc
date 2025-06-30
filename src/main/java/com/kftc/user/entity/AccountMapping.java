package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountMapping extends DateTimeEntity {
    
    @Id
    @Column(name = "fintech_use_num", length = 24)
    private String fintechUseNum; // 핀테크이용번호
    
    @Column(name = "user_seq_no", length = 10, nullable = false)
    private String userSeqNo; // 사용자일련번호
    
    @Column(name = "org_code", length = 10, nullable = false)
    private String orgCode; // 이용기관코드
    
    @Column(name = "bank_code_std", length = 3, nullable = false)
    private String bankCodeStd; // 금융기관표준코드
    
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
    
    @Builder
    public AccountMapping(String fintechUseNum, String userSeqNo, String orgCode, String bankCodeStd,
                         String accountNumMasked, String accountAlias, String accountSeq,
                         String accountHolderName, String accountType, String inquiryAgreeYn,
                         String transferAgreeYn, String regState) {
        this.fintechUseNum = fintechUseNum;
        this.userSeqNo = userSeqNo;
        this.orgCode = orgCode;
        this.bankCodeStd = bankCodeStd;
        this.accountNumMasked = accountNumMasked;
        this.accountAlias = accountAlias;
        this.accountSeq = accountSeq;
        this.accountHolderName = accountHolderName;
        this.accountType = accountType;
        this.inquiryAgreeYn = inquiryAgreeYn;
        this.transferAgreeYn = transferAgreeYn;
        this.regState = regState;
    }
    
    public void updateAccountInfo(String accountAlias, String inquiryAgreeYn, String transferAgreeYn) {
        this.accountAlias = accountAlias;
        this.inquiryAgreeYn = inquiryAgreeYn;
        this.transferAgreeYn = transferAgreeYn;
    }
    
    public void updateRegState(String regState) {
        this.regState = regState;
    }
} 