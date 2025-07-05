package com.kftc.bank.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    
    // 공통 응답 필드
    private String apiTranId;           // API 거래고유번호
    private String apiTranDtm;          // API 거래일시
    private String rspCode;             // 응답코드
    private String rspMessage;          // 응답메시지
    
    // 거래 정보
    private String bankTranId;          // 은행거래고유번호
    private String bankTranDate;        // 거래일자
    private String bankCodeStd;         // 은행코드표준
    private String bankName;            // 은행명
    private String fintechUseNum;       // 핀테크이용번호
    private String accountNumMasked;    // 계좌번호마스킹
    private String accountHolderName;   // 계좌명의자명
    
    // 이체 금액 정보
    private String tranAmt;             // 거래금액
    private String withdrawBankTranId;  // 출금은행거래고유번호
    private String depositBankTranId;   // 입금은행거래고유번호
    
    // 잔액 정보
    private String balanceAmt;          // 거래후잔액
    private String availableAmt;        // 사용가능금액
    
    // 이체 상대방 정보
    private String reqClientName;       // 요청고객성명
    private String reqClientNum;        // 요청고객번호
    private String reqClientBankCode;   // 요청고객은행코드
    private String reqClientAccountNum; // 요청고객계좌번호
    private String reqClientAccountName; // 요청고객계좌명
    
    // 수취인 정보
    private String recvClientName;      // 수취고객성명
    private String recvClientBankCode;  // 수취고객은행코드
    private String recvClientAccountNum; // 수취고객계좌번호
    
    // 추가 정보
    private String transferPurpose;     // 이체목적
    private String tranDtime;           // 거래일시
    private String printContent;        // 인자내용
    private String bankRspCode;         // 은행응답코드
    private String bankRspMessage;      // 은행응답메시지
    
    // 성공 응답 생성
    public static TransferResponse success(String apiTranId, String bankTranId, 
                                         String fintechUseNum, String tranAmt, 
                                         String balanceAmt, String accountHolderName) {
        return TransferResponse.builder()
            .apiTranId(apiTranId)
            .apiTranDtm(getCurrentDateTime())
            .rspCode("A0000")
            .rspMessage("정상처리되었습니다")
            .bankTranId(bankTranId)
            .bankTranDate(getCurrentDate())
            .fintechUseNum(fintechUseNum)
            .tranAmt(tranAmt)
            .balanceAmt(balanceAmt)
            .accountHolderName(accountHolderName)
            .tranDtime(getCurrentDateTime())
            .bankRspCode("000")
            .bankRspMessage("정상처리")
            .build();
    }
    
    // 오류 응답 생성
    public static TransferResponse error(String apiTranId, String errorCode, String errorMessage) {
        return TransferResponse.builder()
            .apiTranId(apiTranId)
            .apiTranDtm(getCurrentDateTime())
            .rspCode(errorCode)
            .rspMessage(errorMessage)
            .bankRspCode("999")
            .bankRspMessage("처리실패")
            .build();
    }
    
    private static String getCurrentDateTime() {
        return java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    
    private static String getCurrentDate() {
        return java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
} 