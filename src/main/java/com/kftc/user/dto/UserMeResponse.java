package com.kftc.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserMeResponse {
    
    @JsonProperty("api_tran_id")
    private String apiTranId;
    
    @JsonProperty("api_tran_dtm")
    private String apiTranDtm;
    
    @JsonProperty("rsp_code")
    private String rspCode;
    
    @JsonProperty("rsp_message")
    private String rspMessage;
    
    @JsonProperty("user_seq_no")
    private String userSeqNo;
    
    @JsonProperty("user_ci")
    private String userCi;
    
    @JsonProperty("user_name")
    private String userName;
    
    @JsonProperty("res_cnt")
    private String resCnt;
    
    @JsonProperty("res_list")
    private List<AccountInfo> resList;
    
    @JsonProperty("inquiry_card_cnt")
    private String inquiryCardCnt;
    
    @JsonProperty("inquiry_card_list")
    private List<CardInfo> inquiryCardList;
    
    @JsonProperty("inquiry_pay_cnt")
    private String inquiryPayCnt;
    
    @JsonProperty("inquiry_pay_list")
    private List<PayInfo> inquiryPayList;
    
    @JsonProperty("inquiry_insurance_cnt")
    private String inquiryInsuranceCnt;
    
    @JsonProperty("inquiry_insurance_list")
    private List<InsuranceInfo> inquiryInsuranceList;
    
    @JsonProperty("inquiry_loan_cnt")
    private String inquiryLoanCnt;
    
    @JsonProperty("inquiry_loan_list")
    private List<LoanInfo> inquiryLoanList;
    
    @Data
    @Builder
    public static class AccountInfo {
        @JsonProperty("fintech_use_num")
        private String fintechUseNum;
        
        @JsonProperty("account_alias")
        private String accountAlias;
        
        @JsonProperty("bank_code_std")
        private String bankCodeStd;
        
        @JsonProperty("bank_code_sub")
        private String bankCodeSub;
        
        @JsonProperty("bank_name")
        private String bankName;
        
        @JsonProperty("savings_bank_name")
        private String savingsBankName;
        
        @JsonProperty("account_num_masked")
        private String accountNumMasked;
        
        @JsonProperty("account_seq")
        private String accountSeq;
        
        @JsonProperty("account_holder_name")
        private String accountHolderName;
        
        @JsonProperty("account_holder_type")
        private String accountHolderType;
        
        @JsonProperty("account_type")
        private String accountType;
        
        @JsonProperty("inquiry_agree_yn")
        private String inquiryAgreeYn;
        
        @JsonProperty("inquiry_agree_dtime")
        private String inquiryAgreeDtime;
        
        @JsonProperty("transfer_agree_yn")
        private String transferAgreeYn;
        
        @JsonProperty("transfer_agree_dtime")
        private String transferAgreeDtime;
        
        @JsonProperty("payer_num")
        private String payerNum;
    }
    
    @Data
    @Builder
    public static class CardInfo {
        @JsonProperty("bank_code_std")
        private String bankCodeStd;
        
        @JsonProperty("member_bank_code")
        private String memberBankCode;
        
        @JsonProperty("inquiry_agree_dtime")
        private String inquiryAgreeDtime;
    }
    
    @Data
    @Builder
    public static class PayInfo {
        @JsonProperty("bank_code_std")
        private String bankCodeStd;
        
        @JsonProperty("inquiry_agree_dtime")
        private String inquiryAgreeDtime;
    }
    
    @Data
    @Builder
    public static class InsuranceInfo {
        @JsonProperty("bank_code_std")
        private String bankCodeStd;
        
        @JsonProperty("inquiry_agree_dtime")
        private String inquiryAgreeDtime;
    }
    
    @Data
    @Builder
    public static class LoanInfo {
        @JsonProperty("bank_code_std")
        private String bankCodeStd;
        
        @JsonProperty("inquiry_agree_dtime")
        private String inquiryAgreeDtime;
    }
} 