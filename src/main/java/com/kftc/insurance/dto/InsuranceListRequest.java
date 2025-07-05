package com.kftc.insurance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "보험목록조회 요청")
public class InsuranceListRequest {
    
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
     * 보험사 대표코드 (금융기관 공동코드)
     */
    @NotBlank(message = "보험사 대표코드는 필수입니다.")
    private String bankCodeStd;

    /**
     * 직전조회추적정보
     */
    private String beforInquiryTraceInfo;
} 