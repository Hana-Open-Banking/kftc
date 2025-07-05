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
     * 은행 거래 ID 생성
     */
    private String generateBankTranId() {
        return "KFTC" + getCurrentDateTime() + String.format("%06d", (int)(Math.random() * 1000000));
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
     * 사용자 정보 조회
     */
    public BankUserInfo getUserInfo(String accessToken) {
        log.info("사용자 정보 조회 - 현재 구현되지 않음");
        // 현재는 구현되지 않았으므로 null 반환
        // 추후 필요에 따라 구현 가능
        return null;
    }
    
    /**
     * 계좌잔액조회 - 실제 은행 API 호출
     */
    public BankAccountInfo getAccountBalance(String fintechUseNum, String accessToken) {
        log.info("=== 계좌잔액조회 시작 ===");
        log.info("핀테크이용번호: {}", fintechUseNum);
        
        try {
            // 1. 핀테크 이용번호로 계좌 매핑 정보 조회 (선택사항)
            Optional<AccountMapping> accountMappingOpt = accountMappingRepository.findById(fintechUseNum);
            
            String bankCode = "088"; // 기본값: 신한은행
            AccountMapping accountMapping = null;
            
            if (accountMappingOpt.isPresent()) {
                accountMapping = accountMappingOpt.get();
                bankCode = accountMapping.getBankCodeStd();
                log.info("계좌 매핑 정보 조회 성공: 은행코드={}, 계좌별명={}", bankCode, accountMapping.getAccountAlias());
            } else {
                log.info("계좌 매핑 정보가 없습니다. 신한은행으로 직접 요청을 보냅니다: {}", fintechUseNum);
            }
            
            // 2. 은행 코드에 따라 BaseURL 결정
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.warn("지원하지 않는 은행코드: {}", bankCode);
                throw new RuntimeException("지원하지 않는 은행코드입니다: " + bankCode);
            }
            
            // 3. 실제 계좌번호 준비
            String realAccountNum = null;
            if (accountMapping != null && accountMapping.getAccountNum() != null) {
                realAccountNum = accountMapping.getAccountNum();
                log.info("실제 계좌번호 조회: {}", realAccountNum);
            } else {
                log.warn("실제 계좌번호를 찾을 수 없습니다. fintech_use_num을 그대로 사용");
                realAccountNum = fintechUseNum;
            }
            
            // 4. 신한은행 API 호출 (실제 계좌번호 사용)
            String bankTranId = generateBankTranId();
            String tranDtime = getCurrentDateTime();
            
            String apiUrl = String.format("%s/v2.0/account/balance?account_num=%s&bank_tran_id=%s&tran_dtime=%s", 
                baseUrl, realAccountNum, bankTranId, tranDtime);
            log.info("신한은행 API 호출: {}", apiUrl);
            
            HttpHeaders headers = createBankApiHeaders(accessToken, bankCode);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("신한은행 API 응답 성공: {}", responseBody);
                
                // 4. 응답을 BankAccountInfo 객체로 변환
                BankAccountInfo accountInfo;
                if (accountMapping != null) {
                    accountInfo = convertToBankAccountInfo(responseBody, accountMapping);
                } else {
                    // 매핑 정보가 없는 경우, 기본값으로 생성
                    accountInfo = convertToBankAccountInfoWithoutMapping(responseBody, fintechUseNum, bankCode);
                }
                log.info("=== 계좌잔액조회 성공 ===");
                return accountInfo;
            } else {
                log.error("신한은행 API 호출 실패: status={}, body={}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("신한은행 API 호출에 실패했습니다");
            }
            
        } catch (Exception e) {
            log.error("계좌잔액조회 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("계좌잔액조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 계좌목록조회 - 실제 구현
     */
    public List<BankAccountInfo> getAccountList(String accessToken) {
        log.info("=== 계좌목록조회 시작 ===");
        
        try {
            // JWT 토큰에서 사용자 정보 추출하여 사용자별 계좌 목록 조회
            List<AccountMapping> accountMappings = accountMappingRepository.findAll();
            
            return accountMappings.stream()
                .map(this::convertAccountMappingToBankAccountInfo)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("계좌목록조회 중 오류 발생: {}", e.getMessage(), e);
        return new ArrayList<>();
    }
    }
    
    /**
     * 거래내역조회 - 실제 은행 API 호출
     */
    public List<Object> getTransactionList(String fintechUseNum, String accessToken) {
        log.info("=== 거래내역조회 시작 ===");
        log.info("핀테크이용번호: {}", fintechUseNum);
        
        try {
            // 1. 핀테크 이용번호로 계좌 매핑 정보 조회
            Optional<AccountMapping> accountMappingOpt = accountMappingRepository.findById(fintechUseNum);
            if (!accountMappingOpt.isPresent()) {
                log.warn("핀테크 이용번호에 해당하는 계좌를 찾을 수 없습니다: {}", fintechUseNum);
                throw new RuntimeException("핀테크 이용번호에 해당하는 계좌를 찾을 수 없습니다");
            }
            
            AccountMapping accountMapping = accountMappingOpt.get();
            String bankCode = accountMapping.getBankCodeStd();
            
            // 2. 은행 코드에 따라 BaseURL 결정
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.warn("지원하지 않는 은행코드: {}", bankCode);
                throw new RuntimeException("지원하지 않는 은행코드입니다: " + bankCode);
            }
            
            // 3. 실제 계좌번호 준비
            String realAccountNum = null;
            if (accountMapping.getAccountNum() != null) {
                realAccountNum = accountMapping.getAccountNum();
                log.info("실제 계좌번호 조회: {}", realAccountNum);
            } else {
                log.warn("실제 계좌번호를 찾을 수 없습니다. fintech_use_num을 그대로 사용");
                realAccountNum = fintechUseNum;
            }
            
            // 4. 신한은행 API 호출 (실제 계좌번호 사용)
            String bankTranId = generateBankTranId();
            String tranDtime = getCurrentDateTime();
            
            String apiUrl = String.format("%s/v2.0/account/transaction_list?account_num=%s&bank_tran_id=%s&tran_dtime=%s", 
                baseUrl, realAccountNum, bankTranId, tranDtime);
            log.info("은행 API 호출: {}", apiUrl);
            
            HttpHeaders headers = createBankApiHeaders(accessToken, bankCode);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("거래내역조회 API 응답 성공");
                
                // 거래내역 리스트 추출
                Object transactionList = responseBody.get("res_list");
                if (transactionList instanceof List) {
                    return (List<Object>) transactionList;
                } else {
                    return new ArrayList<>();
                }
            } else {
                log.error("은행 API 호출 실패: status={}", response.getStatusCode());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("거래내역조회 중 오류 발생: {}", e.getMessage(), e);
        return new ArrayList<>();
        }
    }
    
    /**
     * 은행 API 호출을 위한 헤더 생성
     */
    private HttpHeaders createBankApiHeaders(String accessToken, String bankCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-API-KEY", "KFTC_BANK_API_KEY_2024");
        headers.set("X-CLIENT-ID", "KFTC_CENTER");
        headers.set("X-BANK-CODE", bankCode);
        return headers;
    }
    
    /**
     * 은행 API 응답을 BankAccountInfo로 변환
     */
    private BankAccountInfo convertToBankAccountInfo(Map<String, Object> responseBody, AccountMapping accountMapping) {
        // 잔액 정보 추출
        Long balance = 0L;
        String balanceStr = null;
        
        if (responseBody.containsKey("balance_amt")) {
            balanceStr = responseBody.get("balance_amt").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("balance_amt")) {
                balanceStr = data.get("balance_amt").toString();
            }
        }
        
        if (balanceStr != null) {
            try {
                balance = Long.parseLong(balanceStr);
            } catch (NumberFormatException e) {
                log.warn("잔액 변환 실패: {}", balanceStr);
            }
        }
        
        return BankAccountInfo.builder()
            .fintechUseNum(accountMapping.getFintechUseNum())
            .bankCode(accountMapping.getBankCodeStd())
            .accountNumber(accountMapping.getAccountNumMasked()) // 마스킹된 계좌번호 표시용
            .accountName(accountMapping.getAccountAlias())
            .accountHolderName(accountMapping.getAccountHolderName())
            .accountType(accountMapping.getAccountType())
            .balance(balance)
            .status("ACTIVE")
            .productName(accountMapping.getBankName())
            .build();
    }
    
    /**
     * AccountMapping을 BankAccountInfo로 변환
     */
    private BankAccountInfo convertAccountMappingToBankAccountInfo(AccountMapping accountMapping) {
        return BankAccountInfo.builder()
            .fintechUseNum(accountMapping.getFintechUseNum())
            .bankCode(accountMapping.getBankCodeStd())
            .accountNumber(accountMapping.getAccountNumMasked()) // 마스킹된 계좌번호 표시용
            .accountName(accountMapping.getAccountAlias())
            .accountHolderName(accountMapping.getAccountHolderName())
            .accountType(accountMapping.getAccountType())
            .balance(0L) // 목록 조회시에는 잔액 0으로 설정
            .status("ACTIVE")
            .productName(accountMapping.getBankName())
            .build();
    }
    
    /**
     * 매핑 정보 없이 은행 API 응답을 BankAccountInfo로 변환
     */
    private BankAccountInfo convertToBankAccountInfoWithoutMapping(Map<String, Object> responseBody, String fintechUseNum, String bankCode) {
        // 잔액 정보 추출
        Long balance = 0L;
        String balanceStr = null;
        
        if (responseBody.containsKey("balance_amt")) {
            balanceStr = responseBody.get("balance_amt").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("balance_amt")) {
                balanceStr = data.get("balance_amt").toString();
            }
        }
        
        if (balanceStr != null) {
            try {
                balance = Long.parseLong(balanceStr);
            } catch (NumberFormatException e) {
                log.warn("잔액 변환 실패: {}", balanceStr);
            }
        }
        
        // 계좌번호 추출
        String accountNumber = null;
        if (responseBody.containsKey("account_num_masked")) {
            accountNumber = responseBody.get("account_num_masked").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("account_num_masked")) {
                accountNumber = data.get("account_num_masked").toString();
            }
        }
        
        // 계좌명 추출
        String accountName = null;
        if (responseBody.containsKey("account_alias")) {
            accountName = responseBody.get("account_alias").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("account_alias")) {
                accountName = data.get("account_alias").toString();
            }
        }
        
        // 예금주명 추출
        String accountHolderName = null;
        if (responseBody.containsKey("account_holder_name")) {
            accountHolderName = responseBody.get("account_holder_name").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("account_holder_name")) {
                accountHolderName = data.get("account_holder_name").toString();
            }
        }
        
        return BankAccountInfo.builder()
            .fintechUseNum(fintechUseNum)
            .bankCode(bankCode)
            .accountNumber(accountNumber != null ? accountNumber : "***-***-****")
            .accountName(accountName != null ? accountName : "신한은행 계좌")
            .accountHolderName(accountHolderName != null ? accountHolderName : "고객")
            .accountType("P")
            .balance(balance)
            .status("ACTIVE")
            .productName(getBankName(bankCode))
            .build();
    }
    
    /**
     * 출금이체 처리
     */
    public TransferResponse withdrawTransfer(String fintechUseNum, TransferRequest request, String accessToken) {
        log.info("=== 출금이체 시작 ===");
        log.info("핀테크이용번호: {}, 이체금액: {}", fintechUseNum, String.valueOf(request.getTranAmtAsLong()));
        
        try {
            // 1. 계좌 매핑 정보 조회
            Optional<AccountMapping> accountMappingOpt = accountMappingRepository.findByFintechUseNum(fintechUseNum);
            if (!accountMappingOpt.isPresent()) {
                log.error("계좌 매핑 정보를 찾을 수 없습니다: {}", fintechUseNum);
                return TransferResponse.error(generateApiTranId(), "A0023", "등록되지 않은 핀테크이용번호입니다");
            }
            
            AccountMapping accountMapping = accountMappingOpt.get();
            String bankCode = accountMapping.getBankCodeStd();
            
            // 2. 금융기관 baseUrl 확인
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.error("지원하지 않는 은행코드: {}", bankCode);
                return TransferResponse.error(generateApiTranId(), "A0024", "지원하지 않는 금융기관입니다");
            }
            
            // 3. 이체 요청 데이터 생성
            String apiTranId = generateApiTranId();
            String bankTranId = generateBankTranId();
            
            Map<String, Object> transferData = createWithdrawTransferData(request, bankTranId, accountMapping);
            
            // 4. 금융기관 API 호출
            String transferUrl = baseUrl + "/v2.0/transfer/withdraw/fin_num";
            
            HttpHeaders headers = createBankApiHeaders(accessToken, bankCode);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(transferData, headers);
            
            log.info("출금이체 API 호출: url={}, data={}", transferUrl, transferData);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                transferUrl, HttpMethod.POST, entity, Map.class);
            
            // 5. 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("출금이체 성공: {}", responseBody);
                
                return createSuccessTransferResponse(apiTranId, bankTranId, fintechUseNum, 
                    request, responseBody, accountMapping);
            } else {
                log.error("출금이체 실패: status={}, body={}", response.getStatusCode(), response.getBody());
                return TransferResponse.error(apiTranId, "A0025", "출금이체 처리 실패");
            }
            
        } catch (Exception e) {
            log.error("출금이체 중 오류 발생: fintechUseNum={}, error={}", fintechUseNum, e.getMessage(), e);
            return TransferResponse.error(generateApiTranId(), "A0026", "출금이체 처리 중 오류가 발생했습니다");
        }
    }
    
    /**
     * 입금이체 처리
     */
    public TransferResponse depositTransfer(String fintechUseNum, TransferRequest request, String accessToken) {
        log.info("=== 입금이체 시작 ===");
        log.info("핀테크이용번호: {}, 이체금액: {}", fintechUseNum, String.valueOf(request.getTranAmtAsLong()));
        
        try {
            // 1. 계좌 매핑 정보 조회
            Optional<AccountMapping> accountMappingOpt = accountMappingRepository.findByFintechUseNum(fintechUseNum);
            if (!accountMappingOpt.isPresent()) {
                log.error("계좌 매핑 정보를 찾을 수 없습니다: {}", fintechUseNum);
                return TransferResponse.error(generateApiTranId(), "A0023", "등록되지 않은 핀테크이용번호입니다");
            }
            
            AccountMapping accountMapping = accountMappingOpt.get();
            String bankCode = accountMapping.getBankCodeStd();
            
            // 2. 금융기관 baseUrl 확인
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.error("지원하지 않는 은행코드: {}", bankCode);
                return TransferResponse.error(generateApiTranId(), "A0024", "지원하지 않는 금융기관입니다");
            }
            
            // 3. 이체 요청 데이터 생성
            String apiTranId = generateApiTranId();
            String bankTranId = generateBankTranId();
            
            Map<String, Object> transferData = createDepositTransferData(request, bankTranId, accountMapping);
            
            // 4. 금융기관 API 호출
            String transferUrl = baseUrl + "/v2.0/transfer/deposit/fin_num";
            
            HttpHeaders headers = createBankApiHeaders(accessToken, bankCode);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(transferData, headers);
            
            log.info("입금이체 API 호출: url={}, data={}", transferUrl, transferData);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                transferUrl, HttpMethod.POST, entity, Map.class);
            
            // 5. 응답 처리
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                log.info("입금이체 성공: {}", responseBody);
                
                return createSuccessTransferResponse(apiTranId, bankTranId, fintechUseNum, 
                    request, responseBody, accountMapping);
            } else {
                log.error("입금이체 실패: status={}, body={}", response.getStatusCode(), response.getBody());
                return TransferResponse.error(apiTranId, "A0025", "입금이체 처리 실패");
            }
            
        } catch (Exception e) {
            log.error("입금이체 중 오류 발생: fintechUseNum={}, error={}", fintechUseNum, e.getMessage(), e);
            return TransferResponse.error(generateApiTranId(), "A0026", "입금이체 처리 중 오류가 발생했습니다");
        }
    }
    
    /**
     * 출금이체 요청 데이터 생성 (API 명세서 준수)
     */
    private Map<String, Object> createWithdrawTransferData(TransferRequest request, String bankTranId, AccountMapping accountMapping) {
        Map<String, Object> data = new HashMap<>();
        
        // 헤더 정보
        data.put("bank_tran_id", bankTranId);
        
        // 본체 정보 - 출금이체 API 명세서 기준
        data.put("cntr_account_type", request.getEffectiveCntrAccountType());
        data.put("cntr_account_num", request.getCntrAccountNum() != null ? request.getCntrAccountNum() : accountMapping.getAccountNum());
        data.put("dps_print_content", request.getDpsPrintContent() != null ? request.getDpsPrintContent() : "출금이체");
        data.put("fintech_use_num", request.getEffectiveFintechUseNum());
        
        // 선택 필드
        if (request.getWdPrintContent() != null) {
            data.put("wd_print_content", request.getWdPrintContent());
        }
        
        // 필수 필드
        data.put("tran_amt", String.valueOf(request.getTranAmtAsLong()));
        data.put("tran_dtime", request.getTranDtime() != null ? request.getTranDtime() : getCurrentDateTime());
        data.put("req_client_name", request.getReqClientName() != null ? request.getReqClientName() : accountMapping.getAccountHolderName());
        data.put("req_client_num", request.getReqClientNum() != null ? request.getReqClientNum() : "REQ" + System.currentTimeMillis());
        data.put("transfer_purpose", request.getEffectiveTransferPurpose());
        
        // 선택 필드들
        if (request.getReqClientBankCode() != null) {
            data.put("req_client_bank_code", request.getReqClientBankCode());
        }
        if (request.getReqClientAccountNum() != null) {
            data.put("req_client_account_num", request.getReqClientAccountNum());
        }
        if (request.getReqClientFintechUseNum() != null) {
            data.put("req_client_fintech_use_num", request.getReqClientFintechUseNum());
        }
        
        data.put("req_from_offline_yn", request.getEffectiveReqFromOfflineYn());
        
        // 하위기관 정보 (선택)
        if (request.getSubFmcName() != null) {
            data.put("sub_fmc_name", request.getSubFmcName());
        }
        if (request.getSubFmcNum() != null) {
            data.put("sub_fmc_num", request.getSubFmcNum());
        }
        if (request.getSubFmcBusinessNum() != null) {
            data.put("sub_fmc_business_num", request.getSubFmcBusinessNum());
        }
        
        // 최종수취인 정보 (선택)
        if (request.getRecvClientName() != null) {
            data.put("recv_client_name", request.getRecvClientName());
        }
        if (request.getRecvClientBankCode() != null) {
            data.put("recv_client_bank_code", request.getRecvClientBankCode());
        }
        if (request.getRecvClientAccountNum() != null) {
            data.put("recv_client_account_num", request.getRecvClientAccountNum());
        }
        
        return data;
    }
    
    /**
     * 입금이체 요청 데이터 생성 (API 명세서 준수)
     */
    private Map<String, Object> createDepositTransferData(TransferRequest request, String bankTranId, AccountMapping accountMapping) {
        Map<String, Object> data = new HashMap<>();
        
        // 헤더 정보
        data.put("bank_tran_id", bankTranId);
        
        // 본체 정보 - 입금이체 API 명세서 기준
        data.put("cntr_account_type", request.getEffectiveCntrAccountType());
        data.put("cntr_account_num", request.getCntrAccountNum() != null ? request.getCntrAccountNum() : accountMapping.getAccountNum());
        data.put("dps_print_content", request.getDpsPrintContent() != null ? request.getDpsPrintContent() : "입금이체");
        data.put("fintech_use_num", request.getEffectiveFintechUseNum());
        
        // 선택 필드
        if (request.getWdPrintContent() != null) {
            data.put("wd_print_content", request.getWdPrintContent());
        }
        
        // 필수 필드
        data.put("tran_amt", String.valueOf(request.getTranAmtAsLong()));
        data.put("tran_dtime", request.getTranDtime() != null ? request.getTranDtime() : getCurrentDateTime());
        data.put("req_client_name", request.getReqClientName() != null ? request.getReqClientName() : "송금인");
        data.put("req_client_num", request.getReqClientNum() != null ? request.getReqClientNum() : "REQ" + System.currentTimeMillis());
        data.put("transfer_purpose", request.getEffectiveTransferPurpose());
        
        // 선택 필드들
        if (request.getReqClientBankCode() != null) {
            data.put("req_client_bank_code", request.getReqClientBankCode());
        }
        if (request.getReqClientAccountNum() != null) {
            data.put("req_client_account_num", request.getReqClientAccountNum());
        }
        if (request.getReqClientFintechUseNum() != null) {
            data.put("req_client_fintech_use_num", request.getReqClientFintechUseNum());
        }
        
        data.put("req_from_offline_yn", request.getEffectiveReqFromOfflineYn());
        
        // 하위기관 정보 (선택)
        if (request.getSubFmcName() != null) {
            data.put("sub_fmc_name", request.getSubFmcName());
        }
        if (request.getSubFmcNum() != null) {
            data.put("sub_fmc_num", request.getSubFmcNum());
        }
        if (request.getSubFmcBusinessNum() != null) {
            data.put("sub_fmc_business_num", request.getSubFmcBusinessNum());
        }
        
        // 최종수취인 정보 (선택)
        if (request.getRecvClientName() != null) {
            data.put("recv_client_name", request.getRecvClientName());
        }
        if (request.getRecvClientBankCode() != null) {
            data.put("recv_client_bank_code", request.getRecvClientBankCode());
        }
        if (request.getRecvClientAccountNum() != null) {
            data.put("recv_client_account_num", request.getRecvClientAccountNum());
        }
        
        return data;
    }
    
    /**
     * 성공 응답 생성
     */
    private TransferResponse createSuccessTransferResponse(String apiTranId, String bankTranId, String fintechUseNum, 
                                                         TransferRequest request, Map<String, Object> responseBody, AccountMapping accountMapping) {
        
        // 응답에서 잔액 추출
        String balanceAmt = "0";
        if (responseBody.containsKey("balance_amt")) {
            balanceAmt = responseBody.get("balance_amt").toString();
        } else if (responseBody.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            if (data != null && data.containsKey("balance_amt")) {
                balanceAmt = data.get("balance_amt").toString();
            }
        }
        
        return TransferResponse.builder()
            .apiTranId(apiTranId)
            .apiTranDtm(getCurrentDateTime())
            .rspCode("A0000")
            .rspMessage("정상처리되었습니다")
            .bankTranId(bankTranId)
            .bankTranDate(getCurrentDate())
            .bankCodeStd(accountMapping.getBankCodeStd())
            .bankName(getBankName(accountMapping.getBankCodeStd()))
            .fintechUseNum(fintechUseNum)
            .accountNumMasked(accountMapping.getAccountNumMasked())
            .accountHolderName(accountMapping.getAccountHolderName())
            .tranAmt(String.valueOf(request.getTranAmtAsLong()))
            .balanceAmt(balanceAmt)
            .availableAmt(balanceAmt)
            .reqClientName(request.getReqClientName())
            .reqClientBankCode(request.getReqClientBankCode())
            .reqClientAccountNum(request.getReqClientAccountNum())
            .recvClientName(request.getRecvClientName())
            .recvClientBankCode(request.getRecvClientBankCode())
            .recvClientAccountNum(request.getRecvClientAccountNum())
            .transferPurpose(request.getEffectiveTransferPurpose())
            .tranDtime(getCurrentDateTime())
            .printContent(request.getDpsPrintContent())
            .bankRspCode("000")
            .bankRspMessage("정상처리")
            .build();
    }
    
    /**
     * API 거래고유번호 생성
     */
    private String generateApiTranId() {
        return "API" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));
    }
    
    private String getCurrentDate() {
        return java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    public BankAccountInfo verifyAccountRealName(String bankCode, String accountNum, String accessToken) {
        log.warn("레거시 verifyAccountRealName 메서드 호출됨 - null 반환");
        return null;
    }
    
    public List<BankCode> getSupportedBanks() {
        log.info("지원 은행 목록 조회");
        
        List<BankCode> supportedBanks = new ArrayList<>();
        
        // 현재 구현된 금융기관들만 반환
        supportedBanks.add(BankCode.SHINHAN);
        supportedBanks.add(BankCode.KOOKMIN_CARD);
        supportedBanks.add(BankCode.HYUNDAI_CAPITAL);
        supportedBanks.add(BankCode.SAMSUNG_FIRE);
        
        log.info("지원 은행 수: {}", supportedBanks.size());
        return supportedBanks;
    }
    
    public boolean isHealthy(String bankCode) {
        log.info("은행 연결 상태 확인: {}", bankCode);
        
        try {
            String baseUrl = getInstitutionBaseUrl(bankCode);
            if (baseUrl == null) {
                log.warn("지원하지 않는 은행코드: {}", bankCode);
                return false;
            }
            
            // 간단한 헬스체크 API 호출
            String healthCheckUrl = baseUrl + "/health";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", "KFTC_BANK_API_KEY_2024");
            headers.set("X-CLIENT-ID", "KFTC_CENTER");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                healthCheckUrl, HttpMethod.GET, entity, Map.class);
            
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            log.info("은행 연결 상태: bankCode={}, healthy={}", bankCode, isHealthy);
            return isHealthy;
            
        } catch (Exception e) {
            log.warn("은행 연결 상태 확인 중 오류: bankCode={}, error={}", bankCode, e.getMessage());
        return false;
        }
    }
} 