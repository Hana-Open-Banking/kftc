package com.kftc.bank.service;

import com.kftc.bank.common.*;
import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.repository.UserConsentFinancialInstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 멀티 금융기관 통합 서비스
 * 사용자가 연동한 모든 금융기관에서 데이터를 수집하여 통합 응답을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankService {
    
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
     * 금융기관 코드별 BaseURL 매핑
     */
    private String getInstitutionBaseUrl(String bankCode) {
        switch (bankCode) {
            case "088": return shinhanBankUrl;      // 신한은행
            case "301": return kookminCardUrl;      // 국민카드
            case "054": return hyundaiCapitalUrl;   // 현대캐피탈
            case "221": return samsungFireUrl;      // 삼성화재
            default:
                log.warn("지원하지 않는 기관 코드: {}", bankCode);
                return null;
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
        return headers;
    }
    
    /**
     * 멀티 기관 사용자정보조회 (오픈뱅킹 표준)
     */
    public Map<String, Object> getUserInfoFromAllInstitutions(String userSeqNo) {
        log.info("멀티 기관 사용자정보조회 시작: userSeqNo={}", userSeqNo);
        
        try {
            // 1. 사용자가 연동한 금융기관 목록 조회
            List<UserConsentFinancialInstitution> consentList = 
                consentRepository.findByUserSeqNoAndRegStatus(userSeqNo, "ACTIVE");
            
            if (consentList.isEmpty()) {
                log.info("연동된 금융기관이 없습니다: userSeqNo={}", userSeqNo);
                return createEmptyResponse(userSeqNo);
            }
            
            log.info("연동된 금융기관 수: {}", consentList.size());
            
            // 2. 각 금융기관에 병렬로 요청
            List<CompletableFuture<InstitutionResponse>> futures = consentList.stream()
                .map(consent -> CompletableFuture.supplyAsync(() -> 
                    requestUserInfoFromInstitution(userSeqNo, consent.getBankCodeStd()), executor))
                .collect(Collectors.toList());
            
            // 3. 모든 응답 수집
            List<InstitutionResponse> responses = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // 4. 응답 통합
            return createIntegratedResponse(userSeqNo, responses);
            
        } catch (Exception e) {
            log.error("멀티 기관 사용자정보조회 중 오류: userSeqNo={}, error={}", userSeqNo, e.getMessage());
            return createErrorResponse(userSeqNo, e.getMessage());
        }
    }
    
    /**
     * 개별 금융기관에 사용자정보 요청
     */
    private InstitutionResponse requestUserInfoFromInstitution(String userSeqNo, String bankCode) {
        try {
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.warn("지원하지 않는 기관: bankCode={}", bankCode);
                return null;
            }
            
            String url = baseUrl + "/v2.0/user/me?user_seq_no=" + userSeqNo;
            
            HttpHeaders headers = createInstitutionAuthHeaders();
            headers.set("X-BANK-CODE", bankCode);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("기관별 요청 시작: bankCode={}, url={}", bankCode, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("기관별 요청 성공: bankCode={}", bankCode);
                return new InstitutionResponse(bankCode, true, response.getBody());
            } else {
                log.warn("기관별 요청 실패: bankCode={}, status={}", bankCode, response.getStatusCode());
                return new InstitutionResponse(bankCode, false, null);
            }
            
        } catch (Exception e) {
            log.warn("기관별 요청 오류: bankCode={}, error={}", bankCode, e.getMessage());
            return new InstitutionResponse(bankCode, false, null);
        }
    }
    
    /**
     * 통합 응답 생성 (오픈뱅킹 표준 형식)
     */
    private Map<String, Object> createIntegratedResponse(String userSeqNo, List<InstitutionResponse> responses) {
        Map<String, Object> result = new HashMap<>();
        
        // 응답 헤더
        result.put("api_tran_id", UUID.randomUUID().toString());
        result.put("api_tran_dtm", getCurrentDateTime());
        result.put("rsp_code", "A0000");
        result.put("rsp_message", "정상처리되었습니다");
        
        // 사용자 기본정보
        result.put("user_seq_no", userSeqNo);
        result.put("user_ci", "TEST_CI_" + userSeqNo);
        result.put("user_name", "테스트사용자");
        
        // 연동 기관별 정보 수집
        List<Map<String, Object>> institutionInfoList = new ArrayList<>();
        int totalAccountCount = 0;
        
        for (InstitutionResponse response : responses) {
            if (response.isSuccess() && response.getData() != null) {
                Map<String, Object> institutionInfo = createInstitutionInfo(response);
                institutionInfoList.add(institutionInfo);
                
                // 계좌 수 집계
                Object resCnt = response.getData().get("res_cnt");
                if (resCnt != null) {
                    totalAccountCount += Integer.parseInt(resCnt.toString());
                }
            }
        }
        
        // 전체 응답 구성
        result.put("res_cnt", String.valueOf(totalAccountCount));
        result.put("institution_list", institutionInfoList);
        
        log.info("통합 응답 생성 완료: userSeqNo={}, 연동기관수={}, 총계좌수={}", 
            userSeqNo, institutionInfoList.size(), totalAccountCount);
        
        return result;
    }
    
    /**
     * 기관별 정보 변환
     */
    private Map<String, Object> createInstitutionInfo(InstitutionResponse response) {
        Map<String, Object> info = new HashMap<>();
        Map<String, Object> data = response.getData();
        
        info.put("bank_code_std", response.getBankCode());
        info.put("bank_name", getBankName(response.getBankCode()));
        info.put("res_cnt", data.getOrDefault("res_cnt", "0"));
        
        // 계좌 목록이 있으면 포함
        if (data.containsKey("res_list")) {
            info.put("res_list", data.get("res_list"));
        }
        
        return info;
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
    
    /**
     * 빈 응답 생성
     */
    private Map<String, Object> createEmptyResponse(String userSeqNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("api_tran_id", UUID.randomUUID().toString());
        result.put("api_tran_dtm", getCurrentDateTime());
        result.put("rsp_code", "A0000");
        result.put("rsp_message", "연동된 금융기관이 없습니다");
        result.put("user_seq_no", userSeqNo);
        result.put("res_cnt", "0");
        result.put("institution_list", new ArrayList<>());
        return result;
    }
    
    /**
     * 에러 응답 생성
     */
    private Map<String, Object> createErrorResponse(String userSeqNo, String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("api_tran_id", UUID.randomUUID().toString());
        result.put("api_tran_dtm", getCurrentDateTime());
        result.put("rsp_code", "A9999");
        result.put("rsp_message", "시스템 오류: " + errorMessage);
        result.put("user_seq_no", userSeqNo);
        return result;
    }
    
    /**
     * 현재 시간 반환 (yyyyMMddHHmmss)
     */
    private String getCurrentDateTime() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    
    /**
     * 기관별 응답 데이터 클래스
     */
    private static class InstitutionResponse {
        private final String bankCode;
        private final boolean success;
        private final Map<String, Object> data;
        
        public InstitutionResponse(String bankCode, boolean success, Map<String, Object> data) {
            this.bankCode = bankCode;
            this.success = success;
            this.data = data;
        }
        
        public String getBankCode() { return bankCode; }
        public boolean isSuccess() { return success; }
        public Map<String, Object> getData() { return data; }
    }
    
    /**
     * 레거시 메서드들 (호환성 유지)
     */
    public BankUserInfo getUserInfo(String accessToken) {
        log.warn("레거시 getUserInfo 메서드 호출됨 - null 반환");
        return null;
    }
    
    public List<BankAccountInfo> getAccountList(String accessToken) {
        log.warn("레거시 getAccountList 메서드 호출됨 - 빈 리스트 반환");
        return new ArrayList<>();
    }
    
    public BankAccountInfo getAccountBalance(String fintechUseNum, String accessToken) {
        log.warn("레거시 getAccountBalance 메서드 호출됨 - null 반환");
        return null;
    }
    
    public List<Object> getTransactionList(String fintechUseNum, String accessToken) {
        log.warn("레거시 getTransactionList 메서드 호출됨 - 빈 리스트 반환");
        return new ArrayList<>();
    }
    
    public BankAccountInfo withdrawTransfer(String fintechUseNum, TransferRequest request, String accessToken) {
        log.warn("레거시 withdrawTransfer 메서드 호출됨 - null 반환");
        return null;
    }
    
    public BankAccountInfo depositTransfer(String fintechUseNum, TransferRequest request, String accessToken) {
        log.warn("레거시 depositTransfer 메서드 호출됨 - null 반환");
        return null;
    }
    
    public BankAccountInfo verifyAccountRealName(String bankCode, String accountNum, String accessToken) {
        log.warn("레거시 verifyAccountRealName 메서드 호출됨 - null 반환");
        return null;
    }
    
    public List<BankCode> getSupportedBanks() {
        log.warn("레거시 getSupportedBanks 메서드 호출됨 - 빈 리스트 반환");
        return new ArrayList<>();
    }
    
    public boolean isHealthy(String bankCode) {
        log.warn("레거시 isHealthy 메서드 호출됨 - false 반환");
        return false;
    }
} 