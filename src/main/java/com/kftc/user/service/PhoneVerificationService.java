package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.common.util.CiGenerator;
import com.kftc.user.entity.PhoneVerification;
import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.entity.AccountMapping;
import com.kftc.user.repository.PhoneVerificationRepository;
import com.kftc.user.repository.UserConsentFinancialInstitutionRepository;
import com.kftc.user.repository.AccountMappingRepository;
import com.kftc.card.service.CardUserService;
import com.kftc.card.dto.CardListRequest;
import com.kftc.card.dto.CardListResponse;
import com.kftc.bank.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhoneVerificationService {
    
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final CoolSmsService coolSmsService;
    private final UserConsentFinancialInstitutionRepository consentRepository;
    private final AccountMappingRepository accountMappingRepository;
    private final RestTemplate restTemplate;
    private final UserService userService;
    private final CiGenerator ciGenerator;
    private final CardUserService cardUserService;
    private final BankService bankService;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    
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
     * 휴대폰 인증 코드 발송
     */
    public void sendVerificationCode(String phoneNumber) {
        // 기존 인증 코드 삭제
        phoneVerificationRepository.deleteByPhoneNumber(phoneNumber);
        
        // 새 인증 코드 생성
        String verificationCode = coolSmsService.generateVerificationCode();
        
        // SMS 발송
        boolean smsResult = coolSmsService.sendVerificationSms(phoneNumber, verificationCode);
        if (!smsResult) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "SMS 발송에 실패했습니다.");
        }
        
        // 인증 정보 저장 (5분 유효)
        PhoneVerification phoneVerification = PhoneVerification.builder()
                .phoneNumber(phoneNumber)
                .verificationCode(verificationCode)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        
        phoneVerificationRepository.save(phoneVerification);
        
        log.info("휴대폰 인증 코드 발송 완료: phoneNumber={}", phoneNumber);
    }
    
    /**
     * 휴대폰 인증 코드 확인
     */
    public boolean verifyCode(String phoneNumber, String verificationCode) {
        Optional<PhoneVerification> verificationOpt = phoneVerificationRepository
                .findByPhoneNumberAndVerificationCodeAndVerifiedFalse(phoneNumber, verificationCode);
        
        if (verificationOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유효하지 않은 인증 코드입니다.");
        }
        
        PhoneVerification verification = verificationOpt.get();
        
        if (verification.isExpired()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 인증 코드입니다.");
        }
        
        // 인증 완료 처리
        verification.markAsVerified();
        
        log.info("휴대폰 인증 완료: phoneNumber={}", phoneNumber);
        return true;
    }
    
    /**
     * PASS 인증 완료 (CI 포함 응답 + 금융기관 자동 탐색)
     */
    public Object verifyCodeWithPassAuth(String phoneNumber, String verificationCode, 
                                        String userName, String socialSecurityNumber) {
        // 기본 인증 처리
        boolean verified = verifyCode(phoneNumber, verificationCode);
        
        // 사용자 정보가 있으면 CI 포함 응답, 없으면 기본 응답
        if (userName != null && socialSecurityNumber != null) {
            // CI 생성 (실제로는 PASS API에서 받아옴)
            String ci = generateCi(userName, socialSecurityNumber, phoneNumber);
            
            // 생년월일과 성별 추출
            String birthDate = extractBirthDate(socialSecurityNumber);
            String gender = extractGender(socialSecurityNumber);
            
            log.info("PASS 인증 완료: phoneNumber={}, userName={}, ci={}***", 
                    phoneNumber, userName, ci.substring(0, 10));
            
            // 사용자 생성 또는 조회
            String userSeqNo = userService.createOrGetUserByCi(ci, userName, phoneNumber);
            log.info("사용자 생성/조회 완료: userSeqNo={}, ci={}***", userSeqNo, ci.substring(0, 10));
            
            // ⭐ 금융기관 계좌 탐색 (연동하지 않고 탐색만)
            List<Map<String, Object>> availableInstitutions = discoverAvailableFinancialInstitutions(userSeqNo, ci);
            log.info("금융기관 탐색 완료: userSeqNo={}, 발견된기관수={}", userSeqNo, availableInstitutions.size());
            
            // 반환할 데이터 상세 로그
            for (int i = 0; i < availableInstitutions.size(); i++) {
                Map<String, Object> institution = availableInstitutions.get(i);
                log.info("기관 {}번째: {}", i, institution);
                Object accountList = institution.get("accountList");
                if (accountList instanceof List) {
                    List<?> accounts = (List<?>) accountList;
                    log.info("기관 {}번째 계좌 목록 크기: {}", i, accounts.size());
                    for (int j = 0; j < accounts.size(); j++) {
                        log.info("  계좌 {}: {}", j, accounts.get(j));
                    }
                }
            }
            
            // CI 포함 응답 객체 생성
            Map<String, Object> response = new HashMap<>();
            response.put("verified", true);
            response.put("ci", ci);
            response.put("userSeqNo", userSeqNo);
            response.put("userName", userName);
            response.put("phoneNumber", phoneNumber);
            response.put("birthDate", birthDate);
            response.put("gender", gender);
            response.put("availableInstitutions", availableInstitutions);
            response.put("hasAccounts", !availableInstitutions.isEmpty());
            response.put("institutionCount", availableInstitutions.size());
            response.put("requiresConsent", true);
            response.put("canProceedWithoutAccounts", true); // 계좌 없이도 진행 가능
            response.put("timestamp", System.currentTimeMillis());
            
            if (availableInstitutions.isEmpty()) {
                response.put("message", "계좌 정보가 발견되지 않았습니다. 계좌 등록 없이 진행하시겠습니까?");
            } else {
                response.put("message", "계좌 정보가 발견되었습니다. 연동할 계좌를 선택해주세요.");
            }
            
            return response;
        }
        
        // 기본 응답 (기존 호환성 유지)
        return verified;
    }
    
    /**
     * CI 생성 (CiGenerator를 통한 KISA 규격 CI 생성)
     */
    private String generateCi(String userName, String socialSecurityNumber, String phoneNumber) {
        // CiGenerator를 사용하여 KISA 규격에 맞는 CI 생성
        // 실제 주민등록번호를 사용하여 CI 생성
        return ciGenerator.generateCiWithRealRn(socialSecurityNumber);
    }
    
    /**
     * 주민등록번호에서 생년월일 추출 (YYYYMMDD)
     */
    private String extractBirthDate(String socialSecurityNumber) {
        if (socialSecurityNumber.length() < 7) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        String cleanSsn = socialSecurityNumber.replace("-", "");
        String yearPrefix = cleanSsn.substring(0, 2);
        String monthDay = cleanSsn.substring(2, 6);
        
        // 뒷자리 첫 번째 숫자로 연도 판단
        char genderDigit = cleanSsn.charAt(6);
        String year;
        
        if (genderDigit == '1' || genderDigit == '2') {
            year = "19" + yearPrefix;
        } else if (genderDigit == '3' || genderDigit == '4') {
            year = "20" + yearPrefix;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        return year + monthDay;
    }
    
    /**
     * 주민등록번호에서 성별 추출
     */
    private String extractGender(String socialSecurityNumber) {
        if (socialSecurityNumber.length() < 7) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        String cleanSsn = socialSecurityNumber.replace("-", "");
        char genderDigit = cleanSsn.charAt(6);
        return (genderDigit == '1' || genderDigit == '3') ? "M" : "F";
    }
    
    /**
     * 휴대폰 인증 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isPhoneVerified(String phoneNumber) {
        Optional<PhoneVerification> verificationOpt = phoneVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        
        return verificationOpt.map(PhoneVerification::isVerified).orElse(false);
    }
    
    /**
     * 모든 금융기관에서 계좌 탐색 (연동 없이 발견만)
     */
    public List<Map<String, Object>> discoverAvailableFinancialInstitutions(String userSeqNo, String userCi) {
        log.info("🔍 ============= 금융기관 서비스 탐색 시작 =============");
        log.info("🔍 userSeqNo={}, ci={}...", userSeqNo, userCi.substring(0, Math.min(10, userCi.length())));
        
        List<FinancialInstitution> institutions = Arrays.asList(
            new FinancialInstitution("088", "신한은행", shinhanBankUrl, "BANK"),
            new FinancialInstitution("301", "국민카드", kookminCardUrl, "CARD"),
            new FinancialInstitution("054", "현대캐피탈", hyundaiCapitalUrl, "CARD"),
            new FinancialInstitution("221", "삼성화재", samsungFireUrl, "INSURANCE")
        );
        
        log.info("🔍 설정된 금융기관 목록:");
        for (FinancialInstitution inst : institutions) {
            log.info("  - {}: {} ({})", inst.code, inst.name, inst.baseUrl);
        }
        
        List<Map<String, Object>> availableInstitutions = new ArrayList<>();
        
        // 순차적으로 처리 - DB 저장하지 않음
        for (FinancialInstitution institution : institutions) {
            try {
                log.info("금융기관 확인 중: bankCode={}, bankName={}, baseUrl={}", 
                    institution.code, institution.name, institution.baseUrl);
                
                InstitutionDiscoveryResult result = checkAccountExistence(userSeqNo, userCi, institution);
                if (result.hasService() && result.getResponseData() != null) {
                    // 계좌 목록을 상세하게 포함하여 반환
                    Map<String, Object> institutionInfo = new HashMap<>();
                    institutionInfo.put("bankCode", result.getBankCode());
                    institutionInfo.put("bankName", result.getBankName());
                    String serviceType = getServiceTypeByBankCode(result.getBankCode());
                    institutionInfo.put("serviceType", serviceType);
                    institutionInfo.put("accountList", extractAccountListFromResponse(result.getResponseData(), serviceType));
                    institutionInfo.put("accountCount", getAccountCountFromResponse(result.getResponseData()));
                    institutionInfo.put("accountTypes", getAccountTypesFromResponse(result.getResponseData()));
                    availableInstitutions.add(institutionInfo);
                    
                    log.info("금융기관 서비스 확인 성공: bankCode={}, 계좌수={}", 
                        institution.code, institutionInfo.get("accountCount"));
                } else {
                    log.warn("금융기관 서비스 확인 실패: bankCode={}, hasService={}", 
                        institution.code, result.hasService());
                }
            } catch (Exception e) {
                log.error("금융기관 서비스 확인 중 오류: bankCode={}, error={}", 
                    institution.code, e.getMessage(), e);
            }
        }
        
        log.info("금융기관 서비스 탐색 완료: userSeqNo={}, 이용가능기관수={}", 
            userSeqNo, availableInstitutions.size());
        
        // 계좌 정보가 없어도 정상 처리
        if (availableInstitutions.isEmpty()) {
            log.info("📋 사용자 {}에 대한 계좌 정보가 발견되지 않았습니다. 계좌 등록 없이 진행합니다.", userSeqNo);
        } else {
            // 반환할 데이터 상세 로그
            for (int i = 0; i < availableInstitutions.size(); i++) {
                Map<String, Object> institution = availableInstitutions.get(i);
                log.info("기관 {}번째: {}", i, institution);
                Object accountListObj = institution.get("accountList");
                if (accountListObj instanceof List) {
                    List<?> accounts = (List<?>) accountListObj;
                    log.info("기관 {}번째 계좌 목록 크기: {}", i, accounts.size());
                    for (int j = 0; j < accounts.size(); j++) {
                        log.info("  계좌 {}: {}", j, accounts.get(j));
                    }
                }
            }
        }
        
        return availableInstitutions;
    }
    
    /**
     * 응답에서 계좌 목록 추출 (사용자가 선택할 수 있도록)
     */
    private List<Map<String, Object>> extractAccountListFromResponse(Map<String, Object> responseData, String serviceType) {
        log.info("계좌 목록 추출 시작: serviceType={}, responseData 키들={}", 
            serviceType, responseData != null ? responseData.keySet() : "null");
        
        List<Map<String, Object>> accountList = new ArrayList<>();
        
        if (responseData == null) {
            log.warn("responseData가 null입니다");
            return accountList;
        }
        
        Object resListObj = responseData.get("res_list");
        log.info("res_list 객체: {}, 타입: {}", resListObj, resListObj != null ? resListObj.getClass().getSimpleName() : "null");
        
        if (!(resListObj instanceof List)) {
            log.warn("res_list가 List가 아닙니다: {}", resListObj);
            return accountList;
        }
        
        List<?> resList = (List<?>) resListObj;
        log.info("res_list 크기: {}", resList.size());
        
        for (int i = 0; i < resList.size(); i++) {
            Object item = resList.get(i);
            log.info("계좌 {}번째 아이템: {}", i, item);
            
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> accountData = (Map<String, Object>) item;
                
                log.info("계좌 {}번째 데이터 키들: {}", i, accountData.keySet());
                
                Map<String, Object> account = new HashMap<>();
                
                // 서비스 유형에 따라 다른 필드 추출
                switch (serviceType) {
                    case "BANK":
                        // bank 서버 응답 형식에 맞게 매핑
                        // fintech_use_num이 없으면 payer_num을 사용
                        String fintechUseNum = getString(accountData, "fintech_use_num");
                        if (fintechUseNum == null || fintechUseNum.isEmpty()) {
                            fintechUseNum = getString(accountData, "payer_num");
                        }
                        
                        account.put("fintechUseNum", fintechUseNum);
                        account.put("accountNumMasked", getString(accountData, "account_num_masked"));
                        account.put("accountAlias", getString(accountData, "account_alias"));
                        account.put("accountHolderName", getString(accountData, "account_holder_name"));
                        account.put("accountType", getString(accountData, "account_type"));
                        account.put("bankName", getString(accountData, "bank_name"));
                        account.put("inquiryAgreeYn", getString(accountData, "inquiry_agree_yn"));
                        account.put("transferAgreeYn", getString(accountData, "transfer_agree_yn"));
                        account.put("payerNum", getString(accountData, "payer_num"));
                        
                        log.info("BANK 계좌 추출: fintechUseNum={}, accountAlias={}, bankName={}, 원본키들={}", 
                            account.get("fintechUseNum"), account.get("accountAlias"), account.get("bankName"), accountData.keySet());
                        break;
                        
                    case "CARD":
                        account.put("fintechUseNum", "CARD_" + getString(accountData, "bank_code_std") + "_" + getString(accountData, "card_id"));
                        account.put("accountNumMasked", getString(accountData, "card_num_masked"));
                        account.put("accountAlias", getString(accountData, "card_product_name"));
                        account.put("accountHolderName", getString(accountData, "card_holder_name"));
                        account.put("accountType", "CARD");
                        account.put("bankName", getString(accountData, "card_company_name"));
                        account.put("inquiryAgreeYn", getString(accountData, "inquiry_agree_yn"));
                        account.put("transferAgreeYn", getString(accountData, "bill_agree_yn"));
                        account.put("payerNum", ""); // 카드는 payer_num 없음
                        break;
                        
                    case "INSURANCE":
                        account.put("fintechUseNum", "INS_" + getString(accountData, "bank_code_std") + "_" + getString(accountData, "contract_id"));
                        account.put("accountNumMasked", getString(accountData, "contract_num_masked"));
                        account.put("accountAlias", getString(accountData, "product_name"));
                        account.put("accountHolderName", getString(accountData, "contract_holder_name"));
                        account.put("accountType", "INSURANCE");
                        account.put("bankName", getString(accountData, "insurance_company_name"));
                        account.put("inquiryAgreeYn", getString(accountData, "inquiry_agree_yn"));
                        account.put("transferAgreeYn", getString(accountData, "claim_agree_yn"));
                        account.put("payerNum", ""); // 보험은 payer_num 없음
                        break;
                        
                    default:
                        // 기본값 (은행 형태)
                        account.put("fintechUseNum", getString(accountData, "fintech_use_num"));
                        account.put("accountNumMasked", getString(accountData, "account_num_masked"));
                        account.put("accountAlias", getString(accountData, "account_alias"));
                        account.put("accountHolderName", getString(accountData, "account_holder_name"));
                        account.put("accountType", getString(accountData, "account_type"));
                        account.put("bankName", getString(accountData, "bank_name"));
                        account.put("inquiryAgreeYn", getString(accountData, "inquiry_agree_yn"));
                        account.put("transferAgreeYn", getString(accountData, "transfer_agree_yn"));
                        account.put("payerNum", getString(accountData, "payer_num"));
                        break;
                }
                
                account.put("selected", false); // 기본값: 선택 안됨
                accountList.add(account);
                log.info("계좌 {}번째 추가 완료: {}", i, account);
            }
        }
        
        log.info("계좌 목록 추출 완료: serviceType={}, 총 계좌 수={}", serviceType, accountList.size());
        return accountList;
    }
    
    /**
     * 개별 금융기관에 실제 서비스 유무 확인 요청
     */
    private InstitutionDiscoveryResult checkAccountExistence(String userSeqNo, String userCi, FinancialInstitution institution) {
        try {
            if (institution.baseUrl == null) {
                log.warn("금융기관 URL이 설정되지 않음: bankCode={}", institution.code);
                return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
            // 각 금융기관별로 실제 서비스 유무 확인
            switch (institution.code) {
                case "088": // 신한은행 - 은행 서비스 확인
                    return checkBankService(userSeqNo, userCi, institution);
                case "301": // 국민카드 - 카드 서비스 확인
                case "054": // 현대캐피탈 - 카드 서비스 확인
                    return checkCardService(userSeqNo, userCi, institution);
                case "221": // 삼성화재 - 보험 서비스 확인
                    return checkInsuranceService(userSeqNo, userCi, institution);
                default:
                    log.warn("지원하지 않는 금융기관 코드: {}", institution.code);
                    return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
        } catch (Exception e) {
            log.info("서비스 유무 확인 중 오류: bankCode={}, bankName={}, error={}", 
                institution.code, institution.name, e.getMessage());
            return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
        }
    }
    
    /**
     * 은행 서비스 유무 확인 (신한은행)
     */
    private InstitutionDiscoveryResult checkBankService(String userSeqNo, String userCi, FinancialInstitution institution) {
        try {
            log.info("은행 서비스 확인 시작: bankCode={}, bankName={}", institution.code, institution.name);
            
            // BankService 방식으로 GET 요청 (query parameter 사용)
            String url = institution.baseUrl + "/v2.0/account/list?user_ci=" + userCi;
            
            HttpHeaders headers = createInstitutionAuthHeaders();
            headers.set("X-BANK-CODE", institution.code);
            headers.set("X-SERVICE-TYPE", "BANK");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("은행 API 요청: URL={}", url);
            log.info("은행 API 헤더: {}", headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            
            log.info("은행 API 응답 상태: {}", response.getStatusCode());
            log.info("🔍 은행 API 응답 본문 전체: {}", responseBody);
            
            // Pretty Print로 JSON 출력
            try {
                String prettyJson = objectMapper.writeValueAsString(responseBody);
                log.info("✅ 은행 서버 수신 JSON (Pretty Print):");
                log.info(prettyJson);
            } catch (Exception e) {
                log.warn("JSON Pretty Print 실패: {}", e.getMessage());
            }
            
            if (response.getStatusCode().is2xxSuccessful() && responseBody != null) {
                String rspCode = (String) responseBody.get("rsp_code");
                String rspMessage = (String) responseBody.get("rsp_message");
                Object resCnt = responseBody.get("res_cnt");
                Object resList = responseBody.get("res_list");
                
                log.info("은행 API 응답 분석:");
                log.info("  - rsp_code: {}", rspCode);
                log.info("  - rsp_message: {}", rspMessage);
                log.info("  - res_cnt: {}", resCnt);
                log.info("  - res_list 타입: {}", resList != null ? resList.getClass().getSimpleName() : "null");
                log.info("  - res_list 내용: {}", resList);
                
                // 성공 응답이면 은행 서비스 이용 가능
                boolean hasService = "A0000".equals(rspCode) && resList instanceof List && !((List<?>) resList).isEmpty();
                
                if (hasService) {
                    List<?> accountList = (List<?>) resList;
                    log.info("✅ 은행 서비스 이용 가능 확인: bankCode={}, 계좌수={}", 
                        institution.code, accountList.size());
                    
                    // 받아온 계좌 목록 상세 로그
                    for (int i = 0; i < accountList.size(); i++) {
                        Object account = accountList.get(i);
                        log.info("📋 계좌 {}번째: {}", i + 1, account);
                        
                        // 계좌 정보가 Map이면 더 자세히 로그
                        if (account instanceof Map) {
                            Map<String, Object> accountMap = (Map<String, Object>) account;
                            log.info("  ├─ 계좌번호: {}", accountMap.get("account_num"));
                            log.info("  ├─ 계좌번호(마스킹): {}", accountMap.get("account_num_masked"));
                            log.info("  ├─ 예금주명: {}", accountMap.get("account_holder_name"));
                            log.info("  ├─ 계좌별명: {}", accountMap.get("account_alias"));
                            log.info("  ├─ 계좌유형: {}", accountMap.get("account_type"));
                            log.info("  ├─ 은행명: {}", accountMap.get("bank_name"));
                            log.info("  ├─ 은행코드: {}", accountMap.get("bank_code_std"));
                            log.info("  ├─ 계좌순번: {}", accountMap.get("account_seq"));
                            log.info("  ├─ 조회동의여부: {}", accountMap.get("inquiry_agree_yn"));
                            log.info("  └─ 이체동의여부: {}", accountMap.get("transfer_agree_yn"));
                        }
                    }
                    
                    return new InstitutionDiscoveryResult(institution.code, institution.name, true, responseBody);
                } else {
                    log.warn("❌ 은행 서비스 이용 불가: bankCode={}, rspCode={}, resList 비어있음={}", 
                        institution.code, rspCode, resList == null || (resList instanceof List && ((List<?>) resList).isEmpty()));
                }
            }
            
            return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            
        } catch (Exception e) {
            log.error("은행 서비스 확인 중 오류: bankCode={}, error={}", institution.code, e.getMessage(), e);
            return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
        }
    }
    
    /**
     * 카드 서비스 유무 확인 (국민카드, 현대캐피탈)
     */
    private InstitutionDiscoveryResult checkCardService(String userSeqNo, String userCi, FinancialInstitution institution) {
        try {
            // 카드 목록 조회 API 호출
            String url = institution.baseUrl + "/v2.0/cards?user_ci=" + userCi;
            
            HttpHeaders headers = createInstitutionAuthHeaders();
            headers.set("X-BANK-CODE", institution.code);
            headers.set("X-SERVICE-TYPE", "CARD");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("카드 서비스 확인 요청: bankCode={}, bankName={}, url={}", 
                institution.code, institution.name, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            log.info("카드 API 응답 상태: {}", response.getStatusCode());
            log.info("🔍 카드 API 응답 본문 전체: {}", response.getBody());
            
            // Pretty Print로 JSON 출력
            try {
                String prettyJson = objectMapper.writeValueAsString(response.getBody());
                log.info("💳 카드 서버 수신 JSON (Pretty Print):");
                log.info(prettyJson);
            } catch (Exception e) {
                log.warn("JSON Pretty Print 실패: {}", e.getMessage());
            }
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String rspCode = (String) responseBody.get("rsp_code");
                String rspMessage = (String) responseBody.get("rsp_message");
                Object resList = responseBody.get("res_list");
                
                // 성공 응답이면 카드 서비스 이용 가능
                boolean hasService = "A0000".equals(rspCode);
                
                log.info("카드 API 응답 분석:");
                log.info("  - rsp_code: {}", rspCode);
                log.info("  - rsp_message: {}", rspMessage);
                log.info("  - res_list: {}", resList);
                
                if (hasService && resList instanceof List && !((List<?>) resList).isEmpty()) {
                    List<?> cardList = (List<?>) resList;
                    log.info("✅ 카드 서비스 이용 가능 확인: bankCode={}, 카드수={}", 
                        institution.code, cardList.size());
                    
                    // 받아온 카드 목록 상세 로그
                    for (int i = 0; i < cardList.size(); i++) {
                        Object card = cardList.get(i);
                        log.info("💳 카드 {}번째: {}", i + 1, card);
                        
                        // 카드 정보가 Map이면 더 자세히 로그
                        if (card instanceof Map) {
                            Map<String, Object> cardMap = (Map<String, Object>) card;
                            log.info("  ├─ 카드번호: {}", cardMap.get("card_num"));
                            log.info("  ├─ 카드명: {}", cardMap.get("card_name"));
                            log.info("  ├─ 카드유형: {}", cardMap.get("card_type"));
                            log.info("  ├─ 발급일: {}", cardMap.get("issue_date"));
                            log.info("  └─ 상태: {}", cardMap.get("card_status"));
                        }
                    }
                } else {
                    log.info("❌ 카드 서비스 확인 결과: bankCode={}, bankName={}, hasService={}", 
                        institution.code, institution.name, hasService);
                }
                
                return new InstitutionDiscoveryResult(institution.code, institution.name, hasService, responseBody);
            } else {
                log.info("카드 서비스 확인 결과: bankCode={}, bankName={}, hasService=false - 서비스 없음", 
                    institution.code, institution.name);
                return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
        } catch (Exception e) {
            log.info("카드 서비스 확인 중 오류: bankCode={}, bankName={}, error={}", 
                institution.code, institution.name, e.getMessage());
            return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
        }
    }
    
    /**
     * 보험 서비스 유무 확인 (삼성화재)
     */
    private InstitutionDiscoveryResult checkInsuranceService(String userSeqNo, String userCi, FinancialInstitution institution) {
        try {
            // 보험 계약 목록 조회 API 호출
            String url = institution.baseUrl + "/v2.0/insurance/contracts?user_ci=" + userCi;
            
            HttpHeaders headers = createInstitutionAuthHeaders();
            headers.set("X-BANK-CODE", institution.code);
            headers.set("X-SERVICE-TYPE", "INSURANCE");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("보험 서비스 확인 요청: bankCode={}, bankName={}, url={}", 
                institution.code, institution.name, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            log.info("보험 API 응답 상태: {}", response.getStatusCode());
            log.info("🔍 보험 API 응답 본문 전체: {}", response.getBody());
            
            // Pretty Print로 JSON 출력
            try {
                String prettyJson = objectMapper.writeValueAsString(response.getBody());
                log.info("🛡️ 보험 서버 수신 JSON (Pretty Print):");
                log.info(prettyJson);
            } catch (Exception e) {
                log.warn("JSON Pretty Print 실패: {}", e.getMessage());
            }
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String rspCode = (String) responseBody.get("rsp_code");
                String rspMessage = (String) responseBody.get("rsp_message");
                Object resList = responseBody.get("res_list");
                
                // 성공 응답이면 보험 서비스 이용 가능
                boolean hasService = "A0000".equals(rspCode);
                
                log.info("보험 API 응답 분석:");
                log.info("  - rsp_code: {}", rspCode);
                log.info("  - rsp_message: {}", rspMessage);
                log.info("  - res_list: {}", resList);
                
                if (hasService && resList instanceof List && !((List<?>) resList).isEmpty()) {
                    List<?> insuranceList = (List<?>) resList;
                    log.info("✅ 보험 서비스 이용 가능 확인: bankCode={}, 보험수={}", 
                        institution.code, insuranceList.size());
                    
                    // 받아온 보험 목록 상세 로그
                    for (int i = 0; i < insuranceList.size(); i++) {
                        Object insurance = insuranceList.get(i);
                        log.info("🛡️ 보험 {}번째: {}", i + 1, insurance);
                        
                        // 보험 정보가 Map이면 더 자세히 로그
                        if (insurance instanceof Map) {
                            Map<String, Object> insuranceMap = (Map<String, Object>) insurance;
                            log.info("  ├─ 계약번호: {}", insuranceMap.get("contract_num"));
                            log.info("  ├─ 보험명: {}", insuranceMap.get("insurance_name"));
                            log.info("  ├─ 보험유형: {}", insuranceMap.get("insurance_type"));
                            log.info("  ├─ 가입일: {}", insuranceMap.get("contract_date"));
                            log.info("  └─ 상태: {}", insuranceMap.get("contract_status"));
                        }
                    }
                } else {
                    log.info("❌ 보험 서비스 확인 결과: bankCode={}, bankName={}, hasService={}", 
                        institution.code, institution.name, hasService);
                }
                
                return new InstitutionDiscoveryResult(institution.code, institution.name, hasService, responseBody);
            } else {
                log.info("보험 서비스 확인 결과: bankCode={}, bankName={}, hasService=false - 서비스 없음", 
                    institution.code, institution.name);
                return new InstitutionDiscoveryResult(institution.code, institution.name, false, null);
            }
            
        } catch (Exception e) {
            log.info("보험 서비스 확인 중 오류: bankCode={}, bankName={}, error={}", 
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
        return headers;
    }
    
    /**
     * 금융기관 정보 클래스
     */
    private static class FinancialInstitution {
        String code;
        String name;
        String baseUrl;
        String serviceType;
        
        FinancialInstitution(String code, String name, String baseUrl, String serviceType) {
            this.code = code;
            this.name = name;
            this.baseUrl = baseUrl;
            this.serviceType = serviceType;
        }
    }
    
    /**
     * 금융기관 탐색 결과 클래스
     */
    private static class InstitutionDiscoveryResult {
        private final String bankCode;
        private final String bankName;
        private final boolean hasService;
        private final Map<String, Object> responseData;
        
        public InstitutionDiscoveryResult(String bankCode, String bankName, boolean hasService, Map<String, Object> responseData) {
            this.bankCode = bankCode;
            this.bankName = bankName;
            this.hasService = hasService;
            this.responseData = responseData;
        }
        
        public String getBankCode() { return bankCode; }
        public String getBankName() { return bankName; }
        public boolean hasService() { return hasService; }
        public Map<String, Object> getResponseData() { return responseData; }
    }
    
    /**
     * 응답에서 계좌 수 추출
     */
    private String getAccountCountFromResponse(Map<String, Object> responseData) {
        if (responseData == null) return "0";
        
        Object resCnt = responseData.get("res_cnt");
        return resCnt != null ? resCnt.toString() : "0";
    }
    
    /**
     * 응답에서 계좌 유형 추출
     */
    private List<String> getAccountTypesFromResponse(Map<String, Object> responseData) {
        List<String> accountTypes = new ArrayList<>();
        
        if (responseData == null) return accountTypes;
        
        Object resListObj = responseData.get("res_list");
        if (resListObj instanceof List) {
            List<?> resList = (List<?>) resListObj;
            for (Object item : resList) {
                if (item instanceof Map) {
                    Map<?, ?> account = (Map<?, ?>) item;
                    Object accountType = account.get("account_type");
                    if (accountType != null) {
                        accountTypes.add(accountType.toString());
                    }
                }
            }
        }
        
        return accountTypes.isEmpty() ? Arrays.asList("일반계좌") : accountTypes;
    }
    
    /**
     * 계좌 데이터를 DB에 저장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveAccountDataToDB(String userSeqNo, Map<String, Object> responseData, String bankCode) {
        if (responseData == null) {
            throw new IllegalArgumentException("계좌 데이터가 null입니다");
        }
        
        Object resListObj = responseData.get("res_list");
        if (!(resListObj instanceof List)) {
            throw new IllegalArgumentException("계좌 목록 데이터가 올바르지 않습니다");
        }
        
        List<?> resList = (List<?>) resListObj;
        log.info("계좌 데이터 저장 시작: userSeqNo={}, bankCode={}, accountCount={}", 
            userSeqNo, bankCode, resList.size());
        
        for (Object item : resList) {
            if (!(item instanceof Map)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> accountData = (Map<String, Object>) item;
            
            String fintechUseNum = getString(accountData, "fintech_use_num");
            if (fintechUseNum == null || fintechUseNum.trim().isEmpty()) {
                log.warn("계좌 데이터 저장 스킵: fintech_use_num is empty");
                continue;
            }
            
            log.info("계좌 처리 중: fintechUseNum={}, 길이={}", fintechUseNum, fintechUseNum.length());
            
            try {
                AccountMapping existingAccount = accountMappingRepository.findById(fintechUseNum).orElse(null);
                
                if (existingAccount == null) {
                    log.info("새로운 계좌 생성 시작: fintechUseNum={}", fintechUseNum);
                    AccountMapping newAccount = AccountMapping.builder()
                        .fintechUseNum(fintechUseNum)
                        .userSeqNo(userSeqNo)
                        .orgCode("KFTC")
                        .bankCodeStd(bankCode)
                        .accountNumMasked(getString(accountData, "account_num_masked"))
                        .accountAlias(getString(accountData, "account_alias"))
                        .accountSeq(getString(accountData, "account_seq"))
                        .accountHolderName(getString(accountData, "account_holder_name"))
                        .accountType(getString(accountData, "account_type"))
                        .inquiryAgreeYn(getString(accountData, "inquiry_agree_yn"))
                        .transferAgreeYn(getString(accountData, "transfer_agree_yn"))
                        .regState("ACTIVE")
                        .payerNum(getString(accountData, "payer_num"))
                        .bankName(getString(accountData, "bank_name"))
                        .savingsBankName(getString(accountData, "savings_bank_name"))
                        .inquiryAgreeDtime(getString(accountData, "inquiry_agree_dtime"))
                        .transferAgreeDtime(getString(accountData, "transfer_agree_dtime"))
                        .build();
                    
                    log.info("계좌 저장 시도: fintechUseNum={}", fintechUseNum);
                    accountMappingRepository.saveAndFlush(newAccount); // saveAndFlush 사용
                    log.info("새로운 계좌 정보 저장 완료: fintechUseNum={}", fintechUseNum);
                } else {
                    log.info("기존 계좌 업데이트 시작: fintechUseNum={}", fintechUseNum);
                    existingAccount.updateAccountInfo(
                        getString(accountData, "account_alias"),
                        getString(accountData, "inquiry_agree_yn"),
                        getString(accountData, "transfer_agree_yn")
                    );
                    existingAccount.updateRegState("ACTIVE");
                    accountMappingRepository.saveAndFlush(existingAccount); // saveAndFlush 사용
                    log.info("기존 계좌 정보 업데이트 완료: fintechUseNum={}", fintechUseNum);
                }
            } catch (Exception e) {
                log.error("계좌 데이터 저장 중 오류: fintechUseNum={}, 길이={}, error={}", 
                    fintechUseNum, fintechUseNum.length(), e.getMessage(), e);
                throw new RuntimeException("계좌 데이터 저장 실패: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * 카드 데이터를 DB에 저장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveCardDataToDB(String userSeqNo, Map<String, Object> responseData, String bankCode) {
        try {
            if (responseData == null) return;
            
            Object resListObj = responseData.get("res_list");
            if (!(resListObj instanceof List)) return;
            
            List<?> resList = (List<?>) resListObj;
            for (Object item : resList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cardData = (Map<String, Object>) item;
                    
                    String cardId = getString(cardData, "card_id");
                    if (cardId == null || cardId.trim().isEmpty()) {
                        continue; // card_id가 없으면 스킵
                    }
                    
                    // 카드 ID를 fintech_use_num으로 사용 (카드는 별도 ID 체계)
                    String fintechUseNum = "CARD_" + bankCode + "_" + cardId;
                    
                    // 기존 카드 정보가 있는지 확인
                    AccountMapping existingCard = accountMappingRepository.findById(fintechUseNum).orElse(null);
                    
                    if (existingCard == null) {
                        // 새로운 카드 정보 저장
                        AccountMapping newCard = AccountMapping.builder()
                            .fintechUseNum(fintechUseNum)
                            .userSeqNo(userSeqNo)
                            .orgCode("KFTC")
                            .bankCodeStd(bankCode)
                            .accountNumMasked(getString(cardData, "card_num_masked"))
                            .accountAlias(getString(cardData, "card_alias"))
                            .accountSeq("001")
                            .accountHolderName(getString(cardData, "card_holder_name"))
                            .accountType("C") // Card
                            .inquiryAgreeYn("Y")
                            .transferAgreeYn("N") // 카드는 이체 불가
                            .regState("ACTIVE")
                            .payerNum(null) // 카드는 payer_num 없음
                            .bankName(getBankNameByCode(bankCode))
                            .savingsBankName("")
                            .inquiryAgreeDtime(getCurrentDateTime())
                            .transferAgreeDtime("")
                            .build();
                        
                        accountMappingRepository.save(newCard);
                        
                        log.info("카드 정보 저장 완료: userSeqNo={}, cardId={}, bankCode={}", 
                            userSeqNo, cardId, bankCode);
                    } else {
                        existingCard.updateRegState("ACTIVE");
                        log.info("카드 정보 업데이트 완료: userSeqNo={}, cardId={}, bankCode={}", 
                            userSeqNo, cardId, bankCode);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("카드 데이터 저장 중 오류: userSeqNo={}, bankCode={}, error={}", 
                userSeqNo, bankCode, e.getMessage());
        }
    }
    
    /**
     * 보험 데이터를 DB에 저장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveInsuranceDataToDB(String userSeqNo, Map<String, Object> responseData, String bankCode) {
        try {
            if (responseData == null) return;
            
            Object resListObj = responseData.get("res_list");
            if (!(resListObj instanceof List)) return;
            
            List<?> resList = (List<?>) resListObj;
            for (Object item : resList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> insuranceData = (Map<String, Object>) item;
                    
                    String contractId = getString(insuranceData, "contract_id");
                    if (contractId == null || contractId.trim().isEmpty()) {
                        continue; // contract_id가 없으면 스킵
                    }
                    
                    // 보험 계약 ID를 fintech_use_num으로 사용
                    String fintechUseNum = "INS_" + bankCode + "_" + contractId;
                    
                    // 기존 보험 정보가 있는지 확인
                    AccountMapping existingInsurance = accountMappingRepository.findById(fintechUseNum).orElse(null);
                    
                    if (existingInsurance == null) {
                        // 새로운 보험 정보 저장
                        AccountMapping newInsurance = AccountMapping.builder()
                            .fintechUseNum(fintechUseNum)
                            .userSeqNo(userSeqNo)
                            .orgCode("KFTC")
                            .bankCodeStd(bankCode)
                            .accountNumMasked(getString(insuranceData, "contract_num_masked"))
                            .accountAlias(getString(insuranceData, "product_name"))
                            .accountSeq("001")
                            .accountHolderName(getString(insuranceData, "contract_holder_name"))
                            .accountType("I") // Insurance
                            .inquiryAgreeYn("Y")
                            .transferAgreeYn("N") // 보험은 이체 불가
                            .regState("ACTIVE")
                            .payerNum(null) // 보험은 payer_num 없음
                            .bankName(getBankNameByCode(bankCode))
                            .savingsBankName("")
                            .inquiryAgreeDtime(getCurrentDateTime())
                            .transferAgreeDtime("")
                            .build();
                        
                        accountMappingRepository.save(newInsurance);
                        
                        log.info("보험 정보 저장 완료: userSeqNo={}, contractId={}, bankCode={}", 
                            userSeqNo, contractId, bankCode);
                    } else {
                        existingInsurance.updateRegState("ACTIVE");
                        log.info("보험 정보 업데이트 완료: userSeqNo={}, contractId={}, bankCode={}", 
                            userSeqNo, contractId, bankCode);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("보험 데이터 저장 중 오류: userSeqNo={}, bankCode={}, error={}", 
                userSeqNo, bankCode, e.getMessage());
        }
    }
    
    /**
     * Map에서 안전하게 String 값을 추출
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
    
    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    /**
     * 사용자가 선택한 계좌들만 DB에 저장
     */
    @Transactional
    public Map<String, Object> saveSelectedAccounts(String userSeqNo, List<Map<String, Object>> selectedAccounts) {
        log.info("선택된 계좌 저장 시작: userSeqNo={}, 선택된계좌수={}", userSeqNo, selectedAccounts.size());
        
        int savedCount = 0;
        List<String> savedAccountIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (Map<String, Object> accountInfo : selectedAccounts) {
            try {
                String fintechUseNum = (String) accountInfo.get("fintechUseNum");
                String bankCode = (String) accountInfo.get("bankCode");
                String serviceType = (String) accountInfo.get("serviceType");
                
                if (fintechUseNum == null || fintechUseNum.trim().isEmpty()) {
                    errors.add("fintech_use_num이 없는 계좌가 있습니다.");
                    continue;
                }
                
                // 기존 계좌 확인
                AccountMapping existingAccount = accountMappingRepository.findById(fintechUseNum).orElse(null);
                
                if (existingAccount == null) {
                    // 새로운 계좌 저장
                    AccountMapping newAccount = AccountMapping.builder()
                        .fintechUseNum(fintechUseNum)
                        .userSeqNo(userSeqNo)
                        .orgCode("KFTC")
                        .bankCodeStd(bankCode)
                        .accountNumMasked((String) accountInfo.get("accountNumMasked"))
                        .accountAlias((String) accountInfo.get("accountAlias"))
                        .accountSeq("001")
                        .accountHolderName((String) accountInfo.get("accountHolderName"))
                        .accountType((String) accountInfo.get("accountType"))
                        .inquiryAgreeYn((String) accountInfo.get("inquiryAgreeYn"))
                        .transferAgreeYn((String) accountInfo.get("transferAgreeYn"))
                        .regState("ACTIVE")
                        .payerNum((String) accountInfo.get("payerNum"))
                        .bankName((String) accountInfo.get("bankName"))
                        .savingsBankName("")
                        .inquiryAgreeDtime(getCurrentDateTime())
                        .transferAgreeDtime(getCurrentDateTime())
                        .build();
                    
                    accountMappingRepository.saveAndFlush(newAccount);
                    savedCount++;
                    savedAccountIds.add(fintechUseNum);
                    
                    log.info("새로운 계좌 저장 완료: fintechUseNum={}", fintechUseNum);
                } else {
                    // 기존 계좌 활성화
                    existingAccount.updateRegState("ACTIVE");
                    accountMappingRepository.saveAndFlush(existingAccount);
                    savedCount++;
                    savedAccountIds.add(fintechUseNum);
                    
                    log.info("기존 계좌 활성화 완료: fintechUseNum={}", fintechUseNum);
                }
                
            } catch (Exception e) {
                String error = "계좌 저장 실패: " + e.getMessage();
                errors.add(error);
                log.error("계좌 저장 중 오류: accountInfo={}, error={}", accountInfo, e.getMessage(), e);
            }
        }
        
        // 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("success", errors.isEmpty());
        result.put("savedCount", savedCount);
        result.put("totalCount", selectedAccounts.size());
        result.put("savedAccountIds", savedAccountIds);
        result.put("errors", errors);
        
        log.info("선택된 계좌 저장 완료: userSeqNo={}, 저장성공={}/{}, 오류수={}", 
            userSeqNo, savedCount, selectedAccounts.size(), errors.size());
        
        return result;
    }

    /**
     * 은행 코드로 서비스 타입 반환
     */
    private String getServiceTypeByBankCode(String bankCode) {
        switch (bankCode) {
            case "088": return "BANK";      // 신한은행
            case "301": return "CARD";      // 국민카드
            case "054": return "CARD";      // 현대캐피탈
            case "221": return "INSURANCE"; // 삼성화재
            default: return "UNKNOWN";
        }
    }

    /**
     * 은행 코드로 은행명 반환
     */
    private String getBankNameByCode(String bankCode) {
        switch (bankCode) {
            case "088": return "신한은행";
            case "301": return "국민카드";
            case "054": return "현대캐피탈";
            case "221": return "삼성화재";
            default: return "알 수 없는 기관";
        }
    }

    /**
     * 선택된 금융기관들과 연동 (기존 메서드 - 호환성 유지)
     */
    @Transactional
    public List<String> linkSelectedFinancialInstitutions(String userSeqNo, List<String> selectedBankCodes) {
        log.info("선택된 금융기관 연동 시작: userSeqNo={}, 선택된기관수={}", userSeqNo, selectedBankCodes.size());
        
        List<String> linkedInstitutions = new ArrayList<>();
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        
        for (String bankCode : selectedBankCodes) {
            try {
                // 기존 연동 정보 확인
                boolean alreadyLinked = consentRepository.existsByUserSeqNoAndBankCodeStd(userSeqNo, bankCode);
                
                if (!alreadyLinked) {
                    // 새로 연동 생성
                    UserConsentFinancialInstitution consent = UserConsentFinancialInstitution.builder()
                        .userSeqNo(userSeqNo)
                        .bankCodeStd(bankCode)
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
                    linkedInstitutions.add(bankCode + ":" + getBankNameByCode(bankCode));
                    
                    log.info("금융기관 연동 완료: userSeqNo={}, bankCode={}, bankName={}", 
                        userSeqNo, bankCode, getBankNameByCode(bankCode));
                } else {
                    log.info("이미 연동된 금융기관: userSeqNo={}, bankCode={}, bankName={}", 
                        userSeqNo, bankCode, getBankNameByCode(bankCode));
                    linkedInstitutions.add(bankCode + ":" + getBankNameByCode(bankCode) + " (기존연동)");
                }
                
            } catch (Exception e) {
                log.error("금융기관 연동 실패: userSeqNo={}, bankCode={}, error={}", 
                    userSeqNo, bankCode, e.getMessage());
            }
        }
        
        log.info("선택된 금융기관 연동 완료: userSeqNo={}, 연동된기관수={}, 연동기관={}", 
            userSeqNo, linkedInstitutions.size(), linkedInstitutions);
        
        return linkedInstitutions;
    }
} 