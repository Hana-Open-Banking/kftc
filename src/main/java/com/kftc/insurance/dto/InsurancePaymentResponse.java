package com.kftc.insurance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InsurancePaymentResponse {
    
    /**
     * API 거래고유번호
     */
    @JsonProperty("api_tran_id")
    private String apiTranId;
    
    /**
     * API 거래일시
     */
    @JsonProperty("api_tran_dtm")
    private String apiTranDtm;
    
    /**
     * 응답코드
     */
    @JsonProperty("rsp_code")
    private String rspCode;
    
    /**
     * 응답메시지
     */
    @JsonProperty("rsp_message")
    private String rspMessage;
    
    /**
     * 은행거래고유번호
     */
    @JsonProperty("bank_tran_id")
    private String bankTranId;
    
    /**
     * 은행거래일자
     */
    private String bankTranDate;
    
    /**
     * 은행코드 (표준)
     */
    private String bankCodeTran;
    
    /**
     * 응답코드 (참가기관)
     */
    private String bankRspCode;
    
    /**
     * 응답메시지 (참가기관)
     */
    private String bankRspMessage;
    
    /**
     * 사용자 일련번호
     */
    private String userSeqNo;
    
    /**
     * 납입구분
     */
    @JsonProperty("pay_due")
    private String payDue;
    
    /**
     * 납입주기
     */
    @JsonProperty("pay_cycle")
    private String payCycle;
    
    /**
     * 납입일자(DD)
     */
    @JsonProperty("pay_date")
    private String payDate;
    
    /**
     * 납입종료일자(YYYYMMDD)
     */
    @JsonProperty("pay_end_date")
    private String payEndDate;
    
    /**
     * 납입보험료(KRW)
     */
    @JsonProperty("pay_amt")
    private String payAmt;
    
    /**
     * 납입기관 대표코드
     */
    @JsonProperty("pay_org_code")
    private String payOrgCode;
    
    /**
     * 납입 계좌번호 (선택)
     */
    @JsonProperty("pay_account_num")
    private String payAccountNum;
    
    /**
     * 출력용 납입 계좌번호
     */
    @JsonProperty("pay_account_num_masked")
    private String payAccountNumMasked;
} 