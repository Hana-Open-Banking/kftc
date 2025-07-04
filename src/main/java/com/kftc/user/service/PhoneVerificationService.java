package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.common.util.CiGenerator;
import com.kftc.user.entity.PhoneVerification;
import com.kftc.user.entity.UserConsentFinancialInstitution;
import com.kftc.user.repository.PhoneVerificationRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhoneVerificationService {
    
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final CoolSmsService coolSmsService;
    private final UserConsentFinancialInstitutionRepository consentRepository;
    private final RestTemplate restTemplate;
    private final UserService userService;
    private final CiGenerator ciGenerator;
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
            
            // CI 포함 응답 객체 생성
            return java.util.Map.of(
                "verified", true,
                "ci", ci,
                "userSeqNo", userSeqNo,
                "userName", userName,
                "phoneNumber", phoneNumber,
                "birthDate", birthDate,
                "gender", gender,
                "availableInstitutions", availableInstitutions,
                "requiresConsent", true,
                "timestamp", System.currentTimeMillis()
            );
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
        log.info("금융기관 계좌 탐색 시작: userSeqNo={}, ci={}...", userSeqNo, userCi.substring(0, 10));
        
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
            .collect(Collectors.toList());
        
        // 계좌가 있는 기관들의 정보만 반환 (연동하지 않음)
        List<Map<String, Object>> availableInstitutions = new ArrayList<>();
        
        for (InstitutionDiscoveryResult result : results) {
            if (result.hasAccount()) {
                Map<String, Object> institutionInfo = new HashMap<>();
                institutionInfo.put("bankCode", result.bankCode);
                institutionInfo.put("bankName", result.bankName);
                institutionInfo.put("accountCount", getAccountCountFromResponse(result.responseData));
                institutionInfo.put("accountTypes", getAccountTypesFromResponse(result.responseData));
                institutionInfo.put("selected", false); // 사용자가 선택할 수 있도록
                
                availableInstitutions.add(institutionInfo);
                
                log.info("계좌 보유 기관 발견: userSeqNo={}, bankCode={}, bankName={}", 
                    userSeqNo, result.bankCode, result.bankName);
            }
        }
        
        log.info("금융기관 계좌 탐색 완료: userSeqNo={}, 발견된기관수={}", userSeqNo, availableInstitutions.size());
        
        return availableInstitutions;
    }
    
    /**
     * 모든 금융기관에서 계좌 탐색 및 자동 연동 (동의 완료 후 사용)
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
     * 금융기관 정보 클래스
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
     * 사용자 동의 완료 후 선택된 금융기관들과 연동
     */
    @Transactional
    public List<String> linkSelectedFinancialInstitutions(String userSeqNo, List<String> selectedBankCodes) {
        log.info("선택된 금융기관 연동 시작: userSeqNo={}, selectedBankCodes={}", userSeqNo, selectedBankCodes);
        
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
                    log.info("이미 연동된 금융기관: userSeqNo={}, bankCode={}", userSeqNo, bankCode);
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
    
    /**
     * 은행코드별 은행명 반환
     */
    private String getBankNameByCode(String bankCode) {
        switch (bankCode) {
            case "088": return "신한은행";
            case "301": return "국민카드";
            case "054": return "현대캐피탈";
            case "221": return "삼성화재";
            default: return "기타기관";
        }
    }
} 