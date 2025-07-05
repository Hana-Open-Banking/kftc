package com.kftc.bank.service;

import com.kftc.bank.common.*;
import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.entity.AccountMapping;
import com.kftc.user.repository.UserConsentFinancialInstitutionRepository;
import com.kftc.user.repository.AccountMappingRepository;
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
    private final AccountMappingRepository accountMappingRepository;
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
        log.info("=== 멀티 기관 사용자정보조회 시작 ===");
        log.info("요청된 userSeqNo: [{}]", userSeqNo);
        
        try {
            log.info("=== 사용자정보조회 디버깅 시작 ===");
            log.info("요청된 userSeqNo: [{}]", userSeqNo);
            
            // 1. 직접 계좌 매핑 정보 조회 (실제 계좌 데이터)
            log.info("AccountMapping 테이블에서 userSeqNo로 조회 시작...");
            List<com.kftc.user.entity.AccountMapping> accountMappings = 
                accountMappingRepository.findByUserSeqNo(userSeqNo);
            
            log.info("AccountMapping 조회 결과: 총 {}건", accountMappings.size());
            
            // 조회 결과 상세 로그
            if (!accountMappings.isEmpty()) {
                log.info("조회된 계좌 정보:");
                for (int i = 0; i < accountMappings.size(); i++) {
                    com.kftc.user.entity.AccountMapping mapping = accountMappings.get(i);
                    log.info("  {}번째 계좌: userSeqNo=[{}], fintechUseNum=[{}], bankName=[{}], accountAlias=[{}]", 
                            i+1, mapping.getUserSeqNo(), mapping.getFintechUseNum(), mapping.getBankName(), mapping.getAccountAlias());
                }
            }
            
            if (accountMappings.isEmpty()) {
                log.warn("=== 연동된 계좌가 없습니다 ===");
                log.warn("userSeqNo: [{}]", userSeqNo);
                log.warn("AccountMapping 테이블에서 해당 userSeqNo를 가진 레코드가 없습니다.");
                
                // 전체 AccountMapping 테이블의 첫 5개 레코드 조회해서 디버깅
                log.warn("전체 AccountMapping 테이블 조회 시작...");
                List<com.kftc.user.entity.AccountMapping> allMappings = accountMappingRepository.findAll();
                log.warn("전체 AccountMapping 레코드 수: {}", allMappings.size());
                
                if (!allMappings.isEmpty()) {
                    log.warn("AccountMapping 테이블의 첫 3개 레코드:");
                    for (int i = 0; i < Math.min(3, allMappings.size()); i++) {
                        com.kftc.user.entity.AccountMapping mapping = allMappings.get(i);
                        log.warn("  {}번째: userSeqNo=[{}], fintechUseNum=[{}], bankName=[{}]", 
                                i+1, mapping.getUserSeqNo(), mapping.getFintechUseNum(), mapping.getBankName());
                    }
                } else {
                    log.warn("AccountMapping 테이블이 완전히 비어있습니다!");
                }
                
                log.info("=== 멀티 기관 사용자정보조회 종료 (빈 응답) ===");
                return createEmptyResponse(userSeqNo);
            }
            
            log.info("연동된 계좌 수: {}", accountMappings.size());
            
            // 2. 계좌 매핑 데이터에서 직접 응답 생성 (실제 계좌 데이터 사용)
            Map<String, Object> response = createDirectResponse(userSeqNo, accountMappings);
            log.info("=== 멀티 기관 사용자정보조회 성공 ===");
            return response;
            
        } catch (Exception e) {
            log.error("멀티 기관 사용자정보조회 중 오류: userSeqNo={}, error={}", userSeqNo, e.getMessage(), e);
            log.info("=== 멀티 기관 사용자정보조회 오류 ===");
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
     * 계좌 매핑 데이터에서 직접 응답 생성 (실제 계좌 데이터 사용)
     */
    private Map<String, Object> createDirectResponse(String userSeqNo, List<AccountMapping> accountMappings) {
        log.info("계좌 매핑 데이터로 직접 응답 생성: userSeqNo={}, 계좌수={}", userSeqNo, accountMappings.size());
        
        Map<String, Object> result = new HashMap<>();
        result.put("api_tran_id", UUID.randomUUID().toString());
        result.put("api_tran_dtm", getCurrentDateTime());
        result.put("rsp_code", "A0000");
        result.put("rsp_message", "");
        result.put("user_seq_no", userSeqNo);
        result.put("res_cnt", String.valueOf(accountMappings.size()));
        
        // 계좌 목록 변환
        List<Map<String, Object>> resList = accountMappings.stream()
            .map(account -> {
                Map<String, Object> accountInfo = new HashMap<>();
                accountInfo.put("fintech_use_num", account.getFintechUseNum());
                accountInfo.put("account_alias", account.getAccountAlias());
                accountInfo.put("bank_code_std", account.getBankCodeStd());
                accountInfo.put("bank_code_sub", account.getBankCodeStd() + "001");
                accountInfo.put("bank_name", account.getBankName());
                accountInfo.put("savings_bank_name", account.getSavingsBankName());
                accountInfo.put("account_num_masked", account.getAccountNumMasked());
                accountInfo.put("account_seq", account.getAccountSeq());
                accountInfo.put("account_holder_name", account.getAccountHolderName());
                accountInfo.put("account_holder_type", "P");
                accountInfo.put("account_type", account.getAccountType());
                accountInfo.put("inquiry_agree_yn", account.getInquiryAgreeYn());
                accountInfo.put("inquiry_agree_dtime", account.getInquiryAgreeDtime());
                accountInfo.put("transfer_agree_yn", account.getTransferAgreeYn());
                accountInfo.put("transfer_agree_dtime", account.getTransferAgreeDtime());
                accountInfo.put("payer_num", account.getPayerNum());
                return accountInfo;
            })
            .collect(Collectors.toList());
        
        result.put("res_list", resList);
        
        // 카드/보험/대출 정보는 빈 목록으로 설정
        result.put("inquiry_card_cnt", "0");
        result.put("inquiry_card_list", new ArrayList<>());
        result.put("inquiry_pay_cnt", "0");
        result.put("inquiry_pay_list", new ArrayList<>());
        result.put("inquiry_insurance_cnt", "0");
        result.put("inquiry_insurance_list", new ArrayList<>());
        result.put("inquiry_loan_cnt", "0");
        result.put("inquiry_loan_list", new ArrayList<>());
        
        log.info("계좌 매핑 데이터로 응답 생성 완료: userSeqNo={}, 계좌수={}", userSeqNo, accountMappings.size());
        return result;
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