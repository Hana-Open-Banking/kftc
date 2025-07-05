package com.kftc.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "카드정보변경 응답")
public class CardInfoUpdateResponse {
    
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
    
    @JsonProperty("bank_name")
    @Schema(description = "카드 개설기관명", example = "KB카드")
    private String bankName;
    
    @JsonProperty("user_seq_no")
    @Schema(description = "사용자일련번호", example = "1000000001")
    private String userSeqNo;
    
    @JsonProperty("update_user_email")
    @Schema(description = "변경된 이메일주소", example = "testbed@kftc.or.kr")
    private String updateUserEmail;
} 