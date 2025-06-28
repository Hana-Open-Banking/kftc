package com.kftc.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {
    
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
    
    @JsonProperty("user_info")
    private UserInfo userInfo;
    
    @Data
    @Builder
    public static class UserInfo {
        
        @JsonProperty("user_seq_no")
        private String userSeqNo;
        
        @JsonProperty("user_ci")
        private String userCi;
        
        @JsonProperty("user_name")
        private String userName;
        
        @JsonProperty("user_cell_no")
        private String userCellNo;
        
        @JsonProperty("user_email")
        private String userEmail;
        
        @JsonProperty("user_birth_date")
        private String userBirthDate;
        
        @JsonProperty("user_sex_type")
        private String userSexType;
        
        @JsonProperty("user_type")
        private String userType;
        
        @JsonProperty("join_date")
        private String joinDate;
    }
} 