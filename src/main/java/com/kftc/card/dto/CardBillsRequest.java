package com.kftc.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CardBillsRequest {
    
    /**
     * 은행거래고유번호 (이용기관에서 생성)
     */
    @NotBlank(message = "은행거래고유번호는 필수입니다.")
    private String bankTranId;
    
    /**
     * 사용자 일련번호
     */
    @NotBlank(message = "사용자 일련번호는 필수입니다.")
    private String userSeqNo;
    
    /**
     * 카드사 대표코드 (금융기관 공동코드)
     */
    @NotBlank(message = "카드사 대표코드는 필수입니다.")
    private String bankCodeStd;
    
    /**
     * 회원 금융회사 코드 (금융기관 공동코드)
     */
    @NotBlank(message = "회원 금융회사 코드는 필수입니다.")
    private String memberBankCode;
    
    /**
     * 조회 시작월 (YYYYMM)
     */
    @NotBlank(message = "조회 시작월은 필수입니다.")
    private String fromMonth;
    
    /**
     * 조회 종료월 (YYYYMM)
     */
    @NotBlank(message = "조회 종료월은 필수입니다.")
    private String toMonth;
    
    /**
     * 직전조회추적정보
     */
    private String beforInquiryTraceInfo;
} 