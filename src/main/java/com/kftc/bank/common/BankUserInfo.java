package com.kftc.bank.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankUserInfo {
    private String id;
    private String userId;
    private String name;
    private String phoneNumber;
    private String email;
    private String address;
    private String bankCode;    // 은행 코드
    private String bankName;    // 은행명
} 