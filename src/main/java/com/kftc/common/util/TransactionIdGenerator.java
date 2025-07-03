package com.kftc.common.util;

import com.kftc.common.entity.TransactionLog;
import com.kftc.common.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 오픈뱅킹 거래고유번호(참가기관) 생성 유틸리티
 * 
 * 형식: 이용기관코드(10자리) + "U" + 이용기관부여번호(9자리)
 * 예시: F001234560U4BC34239Z
 * 
 * 규칙:
 * - 총 20자리
 * - 하루 동안 유일성 보장 (DB 중복 검사)
 * - "U"는 이용기관에서 생성한 거래고유번호임을 의미
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionIdGenerator {
    
    private final TransactionLogRepository transactionLogRepository;
    
    @Value("${oauth.client.client-use-code}")
    private String clientUseCode; // F001234560
    
    private static final String GENERATION_CODE = "U"; // 이용기관 생성 구분코드
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RETRY_ATTEMPTS = 10; // 최대 재시도 횟수
    
    // Base36 문자 집합 (0-9, A-Z)
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /**
     * 거래고유번호(참가기관) 생성 (DB 중복 검사 포함)
     * 
     * @return 20자리 거래고유번호 (예: F001234560U4BC34239Z)
     */
    public String generateTransactionId() {
        LocalDate today = LocalDate.now();
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            // 이용기관 부여번호 9자리 생성
            String institutionNumber = generateInstitutionNumber();
            
            // 최종 거래고유번호 조합
            String transactionId = clientUseCode + GENERATION_CODE + institutionNumber;
            
            // DB에서 중복 검사
            if (!transactionLogRepository.existsByTransactionDateAndTransactionId(today, transactionId)) {
                log.debug("거래고유번호 생성 성공 ({}회 시도): {}", attempt, transactionId);
                return transactionId;
            }
            
            log.warn("거래고유번호 중복 발생 ({}회 시도): {}", attempt, transactionId);
        }
        
        // 최대 재시도 횟수 초과 시 예외 발생
        throw new RuntimeException("거래고유번호 생성 실패: " + MAX_RETRY_ATTEMPTS + "회 시도 후 모두 중복 발생");
    }
    
    /**
     * 거래고유번호 생성 및 DB 저장
     * 
     * @param apiName API명
     * @param userSeqNo 사용자일련번호  
     * @param bankCodeStd 카드사대표코드
     * @return 생성된 거래고유번호와 저장된 로그
     */
    public TransactionLog generateAndSaveTransactionId(String apiName, String userSeqNo, String bankCodeStd) {
        String transactionId = generateTransactionId();
        
        TransactionLog transactionLog = TransactionLog.builder()
                .transactionId(transactionId)
                .transactionDate(LocalDate.now())
                .apiName(apiName)
                .userSeqNo(userSeqNo)
                .bankCodeStd(bankCodeStd)
                .transactionStatus(TransactionLog.TransactionStatus.PENDING)
                .build();
                
        TransactionLog savedLog = transactionLogRepository.save(transactionLog);
        
        log.info("거래고유번호 생성 및 저장 완료 - ID: {}, API: {}", transactionId, apiName);
        return savedLog;
    }
    
    /**
     * 이용기관 부여번호 9자리 생성
     * 
     * 구성: 시간 기반(6자리) + 시퀀스(1자리) + 랜덤(2자리)
     * 하루 동안 유일성 보장
     */
    private String generateInstitutionNumber() {
        // 1. 현재 시간 기반 6자리 (시분초밀리초)
        LocalDateTime now = LocalDateTime.now();
        int timeValue = (now.getHour() * 3600 + now.getMinute() * 60 + now.getSecond()) * 1000 + (now.getNano() / 1000000);
        String timeBase = toBase36(timeValue % (36 * 36 * 36 * 36 * 36 * 36), 6); // 6자리로 제한
        
        // 2. 시퀀스 번호 1자리 (0-Z)
        int sequence = SEQUENCE.getAndIncrement() % 36;
        String sequenceBase = BASE36_CHARS.charAt(sequence) + "";
        
        // 3. 랜덤 2자리
        String randomBase = toBase36(RANDOM.nextInt(36 * 36), 2);
        
        return timeBase + sequenceBase + randomBase;
    }
    
    /**
     * 숫자를 Base36으로 변환 (지정된 자릿수)
     */
    private String toBase36(int value, int digits) {
        StringBuilder sb = new StringBuilder();
        int temp = Math.abs(value);
        
        if (temp == 0) {
            sb.append("0");
        } else {
            while (temp > 0) {
                sb.insert(0, BASE36_CHARS.charAt(temp % 36));
                temp /= 36;
            }
        }
        
        // 지정된 자릿수로 맞추기 (앞에 0 패딩)
        while (sb.length() < digits) {
            sb.insert(0, "0");
        }
        
        // 자릿수 초과 시 뒤에서 자르기
        if (sb.length() > digits) {
            sb = new StringBuilder(sb.substring(sb.length() - digits));
        }
        
        return sb.toString();
    }
    
    /**
     * 테스트용 거래고유번호 생성 (여러 개)
     */
    public void generateSampleTransactionIds(int count) {
        log.info("=== 거래고유번호 생성 테스트 ===");
        for (int i = 0; i < count; i++) {
            String transactionId = generateTransactionId();
            log.info("거래고유번호 {}: {}", i + 1, transactionId);
            
            // 잠시 대기 (시간 차이를 위해)
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 거래고유번호 유효성 검증
     */
    public boolean isValidTransactionId(String transactionId) {
        if (transactionId == null || transactionId.length() != 20) {
            return false;
        }
        
        // 이용기관코드 확인
        if (!transactionId.startsWith(clientUseCode)) {
            return false;
        }
        
        // 생성주체구분코드 확인
        if (!transactionId.substring(10, 11).equals(GENERATION_CODE)) {
            return false;
        }
        
        // 이용기관부여번호 형식 확인 (영숫자 9자리)
        String institutionNumber = transactionId.substring(11);
        return institutionNumber.matches("[0-9A-Z]{9}");
    }
} 