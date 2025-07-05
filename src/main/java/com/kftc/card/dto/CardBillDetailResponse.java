package com.kftc.card.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CardBillDetailResponse {
    
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
     * 청구상세 개수
     */
    private String billDetailCnt;
    
    /**
     * 청구상세 목록
     */
    private List<BillDetailInfo> billDetailList;
    
    @Data
    @Builder
    public static class BillDetailInfo {
        /**
         * 카드 식별 값
         */
        private String cardValue;
        
        /**
         * 사용일자 (YYYYMMDD)
         */
        private String paidDate;
        
        /**
         * 사용시간 (hhmmss)
         */
        private String paidTime;
        
        /**
         * 이용금액(원/KRW) (-금액가능)
         */
        private String paidAmt;
        
        /**
         * 마스킹된 가맹점명
         */
        private String merchantNameMasked;
        
        /**
         * 신용판매 수수료(원/KRW) (-금액가능)
         */
        private String creditFeeAmt;
        
        /**
         * 상품 구분 ("01":일시불, "02":신용판매할부, "03":현금서비스)
         */
        private String productType;

        /**
         * 카드 이미지 URL
         */
        private String cardImage;
    }
} 