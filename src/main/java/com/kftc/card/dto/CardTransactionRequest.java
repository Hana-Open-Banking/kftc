package com.kftc.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "카드거래내역조회 요청")
public class CardTransactionRequest {
    
    @NotBlank(message = "은행거래고유번호는 필수입니다.")
    @JsonProperty("bank_tran_id")
    @Schema(description = "거래고유번호(참가기관)", example = "F123456789U4BC34239Z")
    private String bankTranId;
    
    @NotBlank(message = "사용자일련번호는 필수입니다.")
    @JsonProperty("user_seq_no")
    @Schema(description = "사용자일련번호", example = "U123456789")
    private String userSeqNo;
    
    @NotBlank(message = "카드사 대표코드는 필수입니다.")
    @JsonProperty("bank_code_std")
    @Schema(description = "카드사 대표코드", example = "381")
    private String bankCodeStd;
    
    @NotBlank(message = "회원 금융회사 코드는 필수입니다.")
    @JsonProperty("member_bank_code")
    @Schema(description = "회원 금융회사 코드", example = "381")
    private String memberBankCode;
    
    @NotBlank(message = "카드 식별자는 필수입니다.")
    @JsonProperty("card_id")
    @Schema(description = "카드 식별자", example = "CARD001")
    private String cardId;
    
    @NotBlank(message = "조회 시작일자는 필수입니다.")
    @JsonProperty("from_date")
    @Schema(description = "조회 시작일자(YYYYMMDD)", example = "20241201")
    private String fromDate;
    
    @NotBlank(message = "조회 종료일자는 필수입니다.")
    @JsonProperty("to_date")
    @Schema(description = "조회 종료일자(YYYYMMDD)", example = "20241231")
    private String toDate;
    
    @JsonProperty("page_index")
    @Schema(description = "페이지 인덱스 (1부터 시작)", example = "1")
    private String pageIndex = "1";
    
    @JsonProperty("befor_inquiry_trace_info")
    @Schema(description = "직전조회추적정보")
    private String beforInquiryTraceInfo;
} 