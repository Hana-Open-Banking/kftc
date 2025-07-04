package com.kftc.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * KISA 규격에 따른 CI(Connecting Information) 생성 유틸리티
 * CI = HMAC sk ((RN || Padding) ⊕ SA)
 */
@Slf4j
@Component
public class CiGenerator {
    
    // KISA와 공유하는 비밀키 (목업용 - 실제로는 보안 저장소에서 관리)
    private static final String SECRET_KEY_SA = "KISA_MOCK_SECRET_SA_64BYTES_FOR_CI_GENERATION_TEST_ENVIRONMENT_2024";
    private static final String SECRET_KEY_SK = "KISA_MOCK_SECRET_SK_64BYTES_FOR_CI_GENERATION_TEST_ENVIRONMENT_2024";
    
    /**
     * 휴대폰 번호를 기반으로 KISA 규격에 맞는 CI를 생성합니다.
     * 실제 주민등록번호 대신 휴대폰 번호를 이용한 목업 구현
     */
    public String generateCi(String phoneNumber) {
        try {
            // 1. 휴대폰 번호를 주민등록번호 형태로 변환 (목업)
            String mockRn = generateMockRnFromPhone(phoneNumber);
            log.debug("목업 주민등록번호 생성: {}", maskRn(mockRn));
            
            // 2. RN + Padding (512bit로 맞추기)
            byte[] rnWithPadding = createRnWithPadding(mockRn);
            
            // 3. SA (비밀정보) 
            byte[] sa = SECRET_KEY_SA.getBytes(StandardCharsets.UTF_8);
            
            // 4. (RN || Padding) ⊕ SA (XOR 연산)
            byte[] xorResult = xorBytes(rnWithPadding, sa);
            
            // 5. HMAC-SHA512로 CI 생성
            String ci = generateHmacSha512(xorResult, SECRET_KEY_SK);
            
            log.info("CI 생성 완료: phoneNumber={}, ci={}", 
                    maskPhoneNumber(phoneNumber), maskCi(ci));
            
            return ci;
            
        } catch (Exception e) {
            log.error("CI 생성 중 오류 발생: phoneNumber={}", maskPhoneNumber(phoneNumber), e);
            // 오류 시 기본 CI 반환 (호환성 유지)
            return "ERROR_CI_" + phoneNumber.replaceAll("[^0-9]", "") + "_" + System.currentTimeMillis();
        }
    }
    
    /**
     * 실제 주민등록번호를 기반으로 KISA 규격에 맞는 CI를 생성합니다.
     * PASS 인증에서 받은 실제 주민등록번호를 사용
     */
    public String generateCiWithRealRn(String realRn) {
        try {
            // 1. 실제 주민등록번호 검증
            if (realRn == null || realRn.replaceAll("[^0-9]", "").length() != 13) {
                throw new IllegalArgumentException("올바르지 않은 주민등록번호입니다.");
            }
            
            String cleanRn = realRn.replaceAll("[^0-9]", "");
            log.debug("실제 주민등록번호 사용: {}", maskRn(cleanRn));
            
            // 2. RN + Padding (512bit로 맞추기)
            byte[] rnWithPadding = createRnWithPadding(cleanRn);
            
            // 3. SA (비밀정보) 
            byte[] sa = SECRET_KEY_SA.getBytes(StandardCharsets.UTF_8);
            
            // 4. (RN || Padding) ⊕ SA (XOR 연산)
            byte[] xorResult = xorBytes(rnWithPadding, sa);
            
            // 5. HMAC-SHA512로 CI 생성
            String ci = generateHmacSha512(xorResult, SECRET_KEY_SK);
            
            log.info("실제 주민등록번호로 CI 생성 완료: rn={}, ci={}", 
                    maskRn(cleanRn), maskCi(ci));
            
            return ci;
            
        } catch (Exception e) {
            log.error("실제 주민등록번호로 CI 생성 중 오류 발생: rn={}", maskRn(realRn), e);
            throw new RuntimeException("CI 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 휴대폰 번호를 기반으로 목업 주민등록번호 생성
     * 실제 서비스에서는 실제 주민등록번호를 사용해야 함
     */
    private String generateMockRnFromPhone(String phoneNumber) {
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
        
        // 휴대폰 번호 마지막 8자리를 이용하여 생년월일 생성
        if (cleanPhone.length() >= 8) {
            String lastEight = cleanPhone.substring(cleanPhone.length() - 8);
            
            // 년도: 마지막 2자리를 이용 (80~99는 19xx년, 00~30은 20xx년으로 가정)
            int yearSuffix = Integer.parseInt(lastEight.substring(6, 8));
            String year = (yearSuffix >= 80) ? "19" + yearSuffix : 
                         (yearSuffix <= 30) ? "20" + String.format("%02d", yearSuffix) : 
                         "19" + yearSuffix;
            
            // 월: 1~12 범위로 조정
            int month = (Integer.parseInt(lastEight.substring(4, 6)) % 12) + 1;
            
            // 일: 1~28 범위로 조정 (모든 월에 안전한 범위)
            int day = (Integer.parseInt(lastEight.substring(2, 4)) % 28) + 1;
            
            // 성별코드: 휴대폰 번호에 따라 결정
            int genderCode = (cleanPhone.hashCode() % 2 == 0) ? 1 : 2;
            if (year.startsWith("20")) {
                genderCode += 2; // 2000년대생은 3, 4
            }
            
            return year.substring(2) + String.format("%02d%02d", month, day) + genderCode + "000000";
        }
        
        // 기본값 (휴대폰 번호가 짧은 경우)
        return "900101" + ((cleanPhone.hashCode() % 2 == 0) ? "1" : "2") + "000000";
    }
    
    /**
     * RN을 512bit로 패딩
     */
    private byte[] createRnWithPadding(String rn) {
        byte[] rnBytes = rn.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[64]; // 512bit = 64byte
        
        // RN 복사 (최대 13바이트)
        System.arraycopy(rnBytes, 0, padded, 0, Math.min(rnBytes.length, 13));
        
        // 나머지는 0x00으로 패딩 (이미 배열 초기화 시 0으로 설정됨)
        return padded;
    }
    
    /**
     * 두 바이트 배열의 XOR 연산
     */
    private byte[] xorBytes(byte[] a, byte[] b) {
        int length = Math.min(a.length, b.length);
        byte[] result = new byte[length];
        
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        
        return result;
    }
    
    /**
     * HMAC-SHA512로 최종 CI 생성
     */
    private String generateHmacSha512(byte[] data, String secretKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        mac.init(secretKeySpec);
        
        byte[] hmacBytes = mac.doFinal(data);
        
        // Base64 인코딩하여 88바이트 문자열로 생성
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
    
    /**
     * 전화번호 마스킹 (로그용)
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    /**
     * 주민등록번호 마스킹 (로그용)
     */
    private String maskRn(String rn) {
        if (rn == null || rn.length() < 8) {
            return "******";
        }
        return rn.substring(0, 6) + "-*******";
    }
    
    /**
     * CI 마스킹 (로그용)
     */
    private String maskCi(String ci) {
        if (ci == null || ci.length() < 10) {
            return "****";
        }
        return ci.substring(0, 10) + "..." + ci.substring(ci.length() - 10);
    }
} 