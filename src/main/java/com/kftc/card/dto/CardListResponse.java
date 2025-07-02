package com.kftc.card.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CardListResponse {
    
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
     * 은행 응답코드
     */
    private String bankRspCode;
    
    /**
     * 은행 응답메시지
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
     * 카드 개수
     */
    private String cardCnt;
    
    /**
     * 카드 목록
     */
    private List<CardInfo> cardList;
    
    @Data
    @Builder
    public static class CardInfo {
        /**
         * 카드 식별자
         */
        private String cardId;
        
        /**
         * 마스킹된 카드번호
         */
        private String cardNumMasked;
        
        /**
         * 카드 상품명
         */
        private String cardName;
        
        /**
         * 본인/가족 구분 ("1":본인, "2":가족)
         */
        private String cardMemberType;
    }
} 