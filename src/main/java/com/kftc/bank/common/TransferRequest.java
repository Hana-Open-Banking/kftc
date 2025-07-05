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
    
    // 공통 요청 필드
    private String bankTranId;          // 은행거래고유번호
    private String cntcInfoType;        // 연락처정보타입
    private String cntcInfo;            // 연락처정보
    private String reqClientName;       // 요청고객성명
    private String reqClientNum;        // 요청고객번호
    private String reqClientBankCode;   // 요청고객은행코드
    private String reqClientAccountNum; // 요청고객계좌번호
    private String reqClientAccountName; // 요청고객계좌명
    
    // 핀테크 이용번호 (Body에 포함)
    private String fintechUseNum;       // 핀테크이용번호
    
    // 이체 금액 정보
    private String tranAmt;             // 거래금액
    private String withdrawBankTranId;  // 출금은행거래고유번호
    private String depositBankTranId;   // 입금은행거래고유번호
    
    // 수취인 정보
    private String recvClientName;      // 수취고객성명
    private String recvClientBankCode;  // 수취고객은행코드
    private String recvClientAccountNum; // 수취고객계좌번호
    
    // 추가 정보
    private String transferPurpose;     // 이체목적
    private String recvClientNum;       // 수취고객번호
    private String printContent;        // 인자내용
    private String tranDtime;           // 거래일시
    private String reqFromOfflineYn;    // 오프라인 업무여부
    private String subFmcName;          // 하위가맹점명
    private String subFmcNum;           // 하위가맹점번호
    private String subFmcBusinessNum;   // 하위가맹점 사업자등록번호
    private String recvClientFintechUseNum; // 최종수취고객핀테크이용번호
    
    // 기존 필드들 (하위 호환성을 위해 유지)
    private String bankCode;            // 은행 코드
    private String accountNumber;       // 계좌번호
    private String nextBalance;         // 거래 후 잔액
    private Long amount;               // 거래 금액
    private String transferType;        // 거래 유형 (입금/출금)
    private String memo;               // 메모 (선택사항)
    
    // 편의 메서드들
    public Long getTranAmtAsLong() {
        if (tranAmt != null && !tranAmt.isEmpty()) {
            try {
                return Long.parseLong(tranAmt);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return amount != null ? amount : 0L;
    }
    
    public String getTranAmtAsString() {
        if (tranAmt != null && !tranAmt.isEmpty()) {
            return tranAmt;
        }
        return amount != null ? String.valueOf(amount) : "0";
    }
    
    public String getEffectiveBankCode() {
        return recvClientBankCode != null ? recvClientBankCode : bankCode;
    }
    
    public String getEffectiveAccountNumber() {
        return recvClientAccountNum != null ? recvClientAccountNum : accountNumber;
    }
    
    public String getEffectiveRecvClientName() {
        return recvClientName != null ? recvClientName : "수취인";
    }
    
    public String getEffectiveTransferPurpose() {
        return transferPurpose != null ? transferPurpose : "일반이체";
    }
    
    public String getEffectivePrintContent() {
        return printContent != null ? printContent : memo;
    }
    
    public String getEffectiveFintechUseNum() {
        return fintechUseNum != null ? fintechUseNum : "";
    }
} 