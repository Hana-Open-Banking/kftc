package com.kftc.user.service;

import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.repository.UserConsentFinancialInstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 금융기관 자동 탐색 및 연동 서비스
 * PASS 인증 완료 후 모든 금융기관에서 사용자 계좌를 탐색하여 자동 연동
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialInstitutionDiscoveryService {
    
    private final RestTemplate restTemplate;
    private final UserConsentFinancialInstitutionRepository consentRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    // 금융기관별 baseUrl 설정
    @Value("${financial.institutions.shinhan-bank.base-url}")
    private String shinhanBankUrl;
    
    @Value("${financial.institutions.kookmin-card.base-url}")
    private String kookminCardUrl;
    
    @Value("${financial.institutions.hyundai-capital.base-url}")
    private String hyundaiCapitalUrl;
    
    @Value("${financial.institutions.samsung-fire.base-url}")
    private String samsungFireUrl;
    
    /**
     * 금융기관 정보 정의
     */
    private static class FinancialInstitution {
        String code;
        String name;
        String baseUrl;
        
        FinancialInstitution(String code, String name, String baseUrl) {
            this.code = code;
            this.name = name;
            this.baseUrl = baseUrl;
        }
    }
    
    /**
     * PASS 인증 완료 후 모든 금융기관에서 계좌 탐색 및 자동 연동
     */
    @Transactional
    public List<String> discoverAndLinkFinancialInstitutions(String userSeqNo, String userCi) {
        log.info("금융기관 자동 탐색 시작: userSeqNo={}, ci={}...", userSeqNo, userCi.substring(0, 10));
        
        // 모든 금융기관 목록 구성
        List<FinancialInstitution> institutions = Arrays.asList(
            new FinancialInstitution("088", "신한은행", shinhanBankUrl),
            new FinancialInstitution("301", "국민카드", kookminCardUrl),
            new FinancialInstitution("054", "현대캐피탈", hyundaiCapitalUrl),
            new FinancialInstitution("221", "삼성화재", samsungFireUrl)
        );
        
        // 각 금융기관에 병렬로 계좌 보유 확인 요청
        List<CompletableFuture<InstitutionDiscoveryResult>> futures = institutions.stream()
            .map(institution -> CompletableFuture.supplyAsync(() -> 
                checkAccountExistence(userSeqNo, userCi, institution), executor))
            .collect(Collectors.toList());
        
        // 모든 응답 수집
        List<InstitutionDiscoveryResult> results = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .filter(InstitutionDiscoveryResult::hasAccount)
            .collect(Collectors.toList());
        
        // 계좌가 있는 기관들과 자동 연동
        List<String> linkedInstitutions = new ArrayList<>();
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        
        for (InstitutionDiscoveryResult result : results) {
            try {
                // 기존 연동 정보 확인
                boolean alreadyLinked = consentRepository.existsByUserSeqNoAndBankCodeStd(
                    userSeqNo, result.bankCode);
                
                if (!alreadyLinked) {
                    // 새로 연동 생성
                    UserConsentFinancialInstitution consent = UserConsentFinancialInstitution.builder()
                        .userSeqNo(userSeqNo)
                        .bankCodeStd(result.bankCode)
                        .infoPrvdAgmtYn("Y")
                        .infoPrvdAgmtDtime(currentTime)
                        .accountInquiryYn("Y")
                        .transactionInquiryYn("Y")
                        .balanceInquiryYn("Y")
                        .regStatus("ACTIVE")
                        .regDtime(currentTime)
                        .updDtime(currentTime)
                        .build();
                    
                    consentRepository.save(consent);
                    linkedInstitutions.add(result.bankCode + ":" + result.bankName);
                    
                    log.info("금융기관 자동 연동 완료: userSeqNo={}, bankCode={}, bankName={}", 
                        userSeqNo, result.bankCode, result.bankName);
                } else {
                    log.info("이미 연동된 금융기관: userSeqNo={}, bankCode={}, bankName={}", 
                        userSeqNo, result.bankCode, result.bankName);
                    linkedInstitutions.add(result.bankCode + ":" + result.bankName + " (기존연동)");
                }
                
            } catch (Exception e) {
                log.error("금융기관 연동 실패: userSeqNo={}, bankCode={}, error={}", 
                    userSeqNo, result.bankCode, e.getMessage());
            }
        }
        
        log.info("금융기관 자동 탐색 완료: userSeqNo={}, 연동된기관수={}, 연동기관={}", 
            userSeqNo, linkedInstitutions.size(), linkedInstitutions);
        
        return linkedInstitutions;
    }
    
    /**
     * 개별 금융기관에 계좌 보유 확인 요청
     */
    private InstitutionDiscoveryResult checkAccountExistence(String userSeqNo, String userCi, FinancialInstitution institution) {
        try {
            if (institution.baseUrl == null) {
                log.warn("금융기관 URL이 설정되지 않음: bankCode={}", institution.code);
                return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
            // 기존 API 활용: /v2.0/user/me API에 CI 파라미터로 계좌 보유 확인
            String url = institution.baseUrl + "/v2.0/user/me?user_ci=" + userCi;
            
            HttpHeaders headers = createInstitutionAuthHeaders();
            headers.set("X-BANK-CODE", institution.code);
            headers.set("X-DISCOVERY-MODE", "true"); // 탐색 모드 표시
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("계좌 보유 확인 요청 (기존 API 활용): bankCode={}, bankName={}, url={}", 
                institution.code, institution.name, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String rspCode = (String) responseBody.get("rsp_code");
                
                // 성공 응답이면 계좌 보유로 판단
                boolean hasAccount = "A0000".equals(rspCode);
                
                log.info("계좌 보유 확인 결과 (기존 API): bankCode={}, bankName={}, hasAccount={}", 
                    institution.code, institution.name, hasAccount);
                
                return new InstitutionDiscoveryResult(institution.code, institution.name, hasAccount, responseBody);
            } else {
                log.info("계좌 보유 확인 결과 (기존 API): bankCode={}, bankName={}, hasAccount=false - 계좌 없음", 
                    institution.code, institution.name);
                return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
        } catch (Exception e) {
            log.info("계좌 보유 확인 결과 (기존 API): bankCode={}, bankName={}, hasAccount=false - 오류: {}", 
                institution.code, institution.name, e.getMessage());
            return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
        }
    }
    
    /**
     * 기관간 인증 헤더 생성
     */
    private HttpHeaders createInstitutionAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", "KFTC_BANK_API_KEY_2024");
        headers.set("X-CLIENT-ID", "KFTC_CENTER");
        headers.set("X-DISCOVERY-MODE", "true"); // 탐색 모드 표시
        return headers;
    }
    
    /**
     * 금융기관 탐색 결과 클래스
     */
    private static class InstitutionDiscoveryResult {
        private final String bankCode;
        private final String bankName;
        private final boolean hasAccount;
        private final Map<String, Object> responseData;
        
        public InstitutionDiscoveryResult(String bankCode, String bankName, boolean hasAccount, Map<String, Object> responseData) {
            this.bankCode = bankCode;
            this.bankName = bankName;
            this.hasAccount = hasAccount;
            this.responseData = responseData;
        }
        
        public String getBankCode() { return bankCode; }
        public String getBankName() { return bankName; }
        public boolean hasAccount() { return hasAccount; }
        public Map<String, Object> getResponseData() { return responseData; }
    }
    
    /**
     * 특정 사용자의 현재 연동 상태 조회
     */
    @Transactional(readOnly = true)
    public List<String> getCurrentLinkedInstitutions(String userSeqNo) {
        List<UserConsentFinancialInstitution> consentList = 
            consentRepository.findByUserSeqNoAndRegStatus(userSeqNo, "ACTIVE");
        
        return consentList.stream()
            .map(consent -> consent.getBankCodeStd() + ":" + getBankName(consent.getBankCodeStd()))
            .collect(Collectors.toList());
    }
    
    /**
     * 은행코드별 은행명 반환
     */
    private String getBankName(String bankCode) {
        switch (bankCode) {
            case "088": return "신한은행";
            case "301": return "국민카드";
            case "054": return "현대캐피탈";
            case "221": return "삼성화재";
            default: return "기타기관";
        }
    }
} 