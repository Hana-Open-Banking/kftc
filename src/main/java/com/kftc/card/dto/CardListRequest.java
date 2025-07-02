package com.kftc.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "카드목록조회 요청")
public class CardListRequest {
    
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
     * 직전조회추적정보
     */
    private String beforInquiryTraceInfo;
} 