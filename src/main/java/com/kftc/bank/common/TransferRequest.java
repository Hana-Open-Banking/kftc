package com.kftc.bank.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {
    
    // API 명세서 기준 필수 필드들
    
    // 헤더 정보
    @JsonProperty("bank_tran_id")
    private String bankTranId;          // 기관고유번호(API)
    
    // 본체 정보 - 출금이체 API 명세서 기준
    @JsonProperty("cntr_account_type")
    private String cntrAccountType;     // 약정 계좌/결제 구분 (필수)
    
    @JsonProperty("cntr_account_num")
    private String cntrAccountNum;      // 약정 계좌/결제 번호 (필수)
    
    @JsonProperty("dps_print_content")
    private String dpsPrintContent;     // 입금계좌인자내역 (필수)
    
    @JsonProperty("fintech_use_num")
    private String fintechUseNum;       // 출금계좌핀테크이용번호 (필수)
    
    @JsonProperty("wd_print_content")
    private String wdPrintContent;      // 출금계좌인자내역 (선택)
    
    @JsonProperty("tran_amt")
    private String tranAmt;             // 거래금액 (필수)
    
    @JsonProperty("tran_dtime")
    private String tranDtime;           // 요청일시 (필수)
    
    @JsonProperty("req_client_name")
    private String reqClientName;       // 요청고객성명 (필수)
    
    @JsonProperty("req_client_bank_code")
    private String reqClientBankCode;   // 요청고객계좌 개설기관 표준코드 (선택)
    
    @JsonProperty("req_client_account_num")
    private String reqClientAccountNum; // 요청고객계좌번호 (선택)
    
    @JsonProperty("req_client_fintech_use_num")
    private String reqClientFintechUseNum; // 요청고객핀테크이용번호 (선택)
    
    @JsonProperty("req_client_num")
    private String reqClientNum;        // 요청고객일련번호 (필수)
    
    @JsonProperty("req_from_offline_yn")
    private String reqFromOfflineYn;    // 오프라인 영업점 거래 여부 (선택)
    
    @JsonProperty("transfer_purpose")
    private String transferPurpose;     // 출금이체 용도 (필수)
    
    @JsonProperty("sub_fmc_name")
    private String subFmcName;          // 하위기관명 (선택)
    
    @JsonProperty("sub_fmc_num")
    private String subFmcNum;           // 하위기관번호 (선택)
    
    @JsonProperty("sub_fmc_business_num")
    private String subFmcBusinessNum;   // 하위기관 사업자등록번호 (선택)
    
    @JsonProperty("recv_client_name")
    private String recvClientName;      // 최종수취인성명 (선택)
    
    @JsonProperty("recv_client_bank_code")
    private String recvClientBankCode;  // 최종수취인계좌 개설기관 표준코드 (선택)
    
    @JsonProperty("recv_client_account_num")
    private String recvClientAccountNum; // 최종수취인계좌번호 (선택)
    
    // 편의 메서드들
    public Long getTranAmtAsLong() {
        if (tranAmt != null && !tranAmt.isEmpty()) {
            try {
                return Long.parseLong(tranAmt);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
    
    public String getEffectiveFintechUseNum() {
        return fintechUseNum != null ? fintechUseNum : "";
    }
    
    public String getEffectiveTransferPurpose() {
        return transferPurpose != null ? transferPurpose : "ST";
    }
    
    public String getEffectiveCntrAccountType() {
        return cntrAccountType != null ? cntrAccountType : "N";
    }
    
    public String getEffectiveReqFromOfflineYn() {
        return reqFromOfflineYn != null ? reqFromOfflineYn : "N";
    }
} 