package com.kftc.card.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CardBillsResponse {
    
    /**
     * API 거래고유번호
     */
    private String apiTranId;
    
    /**
     * API 거래일시
     */
    private String apiTranDtm;
    
    /**
     * 응답코드
     */
    private String rspCode;
    
    /**
     * 응답메시지
     */
    private String rspMessage;
    
    /**
     * 은행거래고유번호
     */
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
     * 청구 개수
     */
    private String billCnt;
    
    /**
     * 청구 목록
     */
    private List<BillInfo> billList;
    
    @Data
    @Builder
    public static class BillInfo {
        /**
         * 청구년월 (YYYYMM)
         */
        private String chargeMonth;
        
        /**
         * 결제순번
         */
        private String settlementSeqNo;
        
        /**
         * 카드 식별자
         */
        private String cardId;
        
        /**
         * 청구금액 (-금액가능)
         */
        private String chargeAmt;
        
        /**
         * 결제일
         */
        private String settlementDay;
        
        /**
         * 결제년월일 (실제 결제일)
         */
        private String settlementDate;
        
        /**
         * 신용/체크 구분 ("01":신용, "02":체크, "03":신용/체크혼용)
         */
        private String creditCheckType;
    }
} 