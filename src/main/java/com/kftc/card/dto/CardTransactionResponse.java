package com.kftc.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "카드거래내역조회 응답")
public class CardTransactionResponse {
    
    @JsonProperty("api_tran_id")
    @Schema(description = "거래고유번호(API)", example = "2ffd133a-d17a-431d-a6a5")
    private String apiTranId;
    
    @JsonProperty("api_tran_dtm")
    @Schema(description = "거래일시(밀리세컨드)", example = "20190910101921567")
    private String apiTranDtm;
    
    @JsonProperty("rsp_code")
    @Schema(description = "응답코드(API)", example = "A0000")
    private String rspCode;
    
    @JsonProperty("rsp_message")
    @Schema(description = "응답메시지(API)", example = "")
    private String rspMessage;
    
    @JsonProperty("bank_tran_id")
    @Schema(description = "거래고유번호(참가기관)", example = "F123456789U4BC34239Z")
    private String bankTranId;
    
    @JsonProperty("bank_tran_date")
    @Schema(description = "거래일자(참가기관)", example = "20190910")
    private String bankTranDate;
    
    @JsonProperty("bank_code_tran")
    @Schema(description = "응답코드를 부여한 참가기관.표준(대표)코드", example = "381")
    private String bankCodeTran;
    
    @JsonProperty("bank_rsp_code")
    @Schema(description = "응답코드(참가기관)", example = "000")
    private String bankRspCode;
    
    @JsonProperty("bank_rsp_message")
    @Schema(description = "응답메시지(참가기관)", example = "")
    private String bankRspMessage;
    
    @JsonProperty("user_seq_no")
    @Schema(description = "사용자일련번호", example = "U123456789")
    private String userSeqNo;
    
    @JsonProperty("next_page_yn")
    @Schema(description = "다음페이지 존재여부", example = "N")
    private String nextPageYn;
    
    @JsonProperty("befor_inquiry_trace_info")
    @Schema(description = "직전조회추적정보")
    private String beforInquiryTraceInfo;
    
    @JsonProperty("tran_cnt")
    @Schema(description = "거래 개수", example = "10")
    private String tranCnt;
    
    @JsonProperty("tran_list")
    @Schema(description = "거래 목록")
    private List<TransactionInfo> tranList;
    
    @Data
    @Builder
    @Schema(description = "거래 정보")
    public static class TransactionInfo {
        
        @JsonProperty("tran_id")
        @Schema(description = "거래 고유번호", example = "TXN20241202001")
        private String tranId;
        
        @JsonProperty("tran_date")
        @Schema(description = "거래일자(YYYYMMDD)", example = "20241202")
        private String tranDate;
        
        @JsonProperty("tran_time")
        @Schema(description = "거래시간(HHMMSS)", example = "143000")
        private String tranTime;
        
        @JsonProperty("merchant_name")
        @Schema(description = "가맹점명", example = "스타벅스 강남점")
        private String merchantName;
        
        @JsonProperty("merchant_regno")
        @Schema(description = "가맹점 사업자번호", example = "123-45-67890")
        private String merchantRegno;
        
        @JsonProperty("approved_amt")
        @Schema(description = "승인금액", example = "5000")
        private String approvedAmt;
        
        @JsonProperty("tran_type")
        @Schema(description = "거래구분 (1:승인, 2:취소)", example = "1")
        private String tranType;
        
        @JsonProperty("category")
        @Schema(description = "거래 카테고리", example = "FOOD")
        private String category;
        
        @JsonProperty("memo")
        @Schema(description = "메모", example = "커피 구매")
        private String memo;
    }
} 