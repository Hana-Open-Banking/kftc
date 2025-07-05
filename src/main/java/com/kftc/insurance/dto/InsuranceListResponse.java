package com.kftc.insurance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InsuranceListResponse {
    
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
     * 다음페이지 존재여부
     */
    private String nextPageYn;
    
    /**
     * 직전조회추적정보
     */
    private String beforInquiryTraceInfo;
    
    /**
     * 보험 개수
     */
    @JsonProperty("insu_cnt")
    private String insuCnt;
    
    /**
     * 보험 목록
     */
    @JsonProperty("insu_list")
    private List<InsuranceInfo> insuList;
    
    @Data
    @Builder
    public static class InsuranceInfo {
        /**
         * 증권번호
         */
        @JsonProperty("insu_num")
        private String insuNum;
        
        /**
         * 상품명
         */
        @JsonProperty("prod_name")
        private String prodName;
        
        /**
         * 보험종류
         */
        @JsonProperty("insu_type")
        private String insuType;
        
        /**
         * 계약상태
         */
        @JsonProperty("insu_status")
        private String insuStatus;
        
        /**
         * 계약체결일
         */
        @JsonProperty("issue_date")
        private String issueDate;
        
        /**
         * 만기일자
         */
        @JsonProperty("exp_date")
        private String expDate;
    }
} 