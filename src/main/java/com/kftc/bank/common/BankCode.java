package com.kftc.bank.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 은행 기관 코드 정의
 */
@Getter
@RequiredArgsConstructor
public enum BankCode {
    SHINHAN("088", "신한은행", "shinhan"),
    KOOKMIN("004", "국민은행", "kookmin"),
    WOORI("020", "우리은행", "woori"),
    HANA("081", "하나은행", "hana"),
    NH("011", "농협은행", "nh"),
    IBK("003", "기업은행", "ibk"),
    KDB("002", "산업은행", "kdb"),
    KAKAO("090", "카카오뱅크", "kakao"),
    TOSS("092", "토스뱅크", "toss"),
    KOOKMIN_CARD("301", "국민카드", "kookmin-card"),
    HYUNDAI_CAPITAL("054", "현대캐피탈", "hyundai-capital"),
    SAMSUNG_FIRE("221", "삼성화재", "samsung-fire");
    
    private final String code;          // 기관 코드
    private final String bankName;      // 은행명
    private final String configKey;     // 설정 키
    
    /**
     * 기관 코드로 BankCode 찾기
     */
    public static BankCode fromCode(String code) {
        for (BankCode bankCode : values()) {
            if (bankCode.getCode().equals(code)) {
                return bankCode;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 은행 코드입니다: " + code);
    }
    
    /**
     * 설정 키로 BankCode 찾기
     */
    public static BankCode fromConfigKey(String configKey) {
        for (BankCode bankCode : values()) {
            if (bankCode.getConfigKey().equals(configKey)) {
                return bankCode;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 설정 키입니다: " + configKey);
    }
} 