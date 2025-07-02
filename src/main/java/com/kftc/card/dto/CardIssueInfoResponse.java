package com.kftc.card.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CardIssueInfoResponse {
    
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
     * 카드 구분 ("01":신용, "02":체크, "03":소액신용체크)
     */
    private String cardType;
    
    /**
     * 결제은행 대표코드
     */
    private String settlementBankCode;
    
    /**
     * 결제 계좌번호
     */
    private String settlementAccountNum;
    
    /**
     * 마스킹된 출력용 결제 계좌번호
     */
    private String settlementAccountNumMasked;
    
    /**
     * 카드 발급일자 (YYYYMMDD)
     */
    private String issueDate;
} 