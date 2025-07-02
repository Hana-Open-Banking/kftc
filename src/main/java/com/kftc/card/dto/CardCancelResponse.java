package com.kftc.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "카드조회해지 응답")
public class CardCancelResponse {
    
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
    @Schema(description = "응답코드를 부여한 참가기관.표준(대표)코드", example = "091")
    private String bankCodeTran;
    
    @JsonProperty("bank_rsp_code")
    @Schema(description = "응답코드(참가기관)", example = "000")
    private String bankRspCode;
    
    @JsonProperty("bank_rsp_message")
    @Schema(description = "응답메시지(참가기관)", example = "")
    private String bankRspMessage;
} 