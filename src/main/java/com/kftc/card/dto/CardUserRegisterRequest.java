package com.kftc.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "카드사용자등록 요청")
public class CardUserRegisterRequest {
    
    @JsonProperty("bank_tran_id")
    @NotBlank
    @Size(max = 20)
    @Schema(description = "거래고유번호(참가기관)", example = "F123456789U4BC34239Z")
    private String bankTranId;
    
    @JsonProperty("bank_code_std")
    @NotBlank
    @Size(max = 3)
    @Schema(description = "카드사 대표코드 (금융기관 공동코드)", example = "097")
    private String bankCodeStd;
    
    @JsonProperty("member_bank_code")
    @NotBlank
    @Size(max = 3)
    @Schema(description = "회원 금융회사 코드 (금융기관 공동코드)", example = "097")
    private String memberBankCode;
    
    @JsonProperty("user_name")
    @NotBlank
    @Size(max = 20)
    @Schema(description = "사용자명", example = "홍길동")
    private String userName;
    
    @JsonProperty("user_ci")
    @NotBlank
    @Size(max = 100)
    @Schema(description = "CI(Connecting Information)", example = "base64encodedCI")
    private String userCi;
    
    @JsonProperty("user_email")
    @NotBlank
    @Email
    @Size(max = 100)
    @Schema(description = "이메일주소", example = "user@example.com")
    private String userEmail;
    
    @NotBlank
    @Schema(description = "서비스구분 - cardinfo: 카드정보조회", example = "cardinfo")
    private String scope;
    
    @JsonProperty("info_prvd_agmt_yn")
    @NotBlank
    @Size(max = 1)
    @Schema(description = "제3자정보제공동의여부", example = "Y")
    private String infoPrvdAgmtYn;
} 