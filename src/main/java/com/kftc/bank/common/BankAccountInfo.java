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
public class BankAccountInfo {
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String accountType;
    private String fintechUseNum;
    private Integer id;
    private Long balance;
    private String status;
    private String productName;
    private String accountHolderName;
} 