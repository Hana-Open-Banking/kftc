package com.kftc.bank.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {
    private String bankCode;        // 은행 코드
    private String accountNumber;   // 계좌번호
    private String nextBalance;     // 거래 후 잔액
    private Long amount;           // 거래 금액
    private String transferType;    // 거래 유형 (입금/출금)
    private String memo;           // 메모 (선택사항)
} 