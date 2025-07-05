package com.kftc.insurance.service;

import com.kftc.insurance.dto.*;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceCompanyService {
    
    private final RestTemplate restTemplate;
    private final UserService userService;
    
    @Value("${financial.institutions.samsung-fire.base-url}")
    private String samsungInsuranceServerUrl;
    
    /**
     * 보험사 코드에 따른 서버 URL 반환
     */
    private String getInsuranceCompanyUrl(String bankCodeStd) {
        return switch (bankCodeStd) {
            case "449" -> samsungInsuranceServerUrl; // 삼성화재
            // 다른 보험사들도 추가 가능
            default -> {
                log.error("지원하지 않는 보험사 코드: {}", bankCodeStd);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        };
    }
    
    /**
     * 보험사별 유효한 보험 목록 확인
     */
    public boolean hasValidInsurances(String bankCodeStd, String userCi) {
        // 실제로는 보험사별 API를 호출하여 유효한 보험 목록을 확인해야 함
        // 여기서는 기본적으로 true 반환 (보험사 서버에서 최종 검증)
        log.debug("보험 유효성 확인 - bankCodeStd: {}, userCi: {}", bankCodeStd, userCi);
        return true;
    }

    /**
     * 보험사 서버로 보험목록조회 요청
     */
    public InsuranceListResponse getInsuranceList(InsuranceListRequest request, String authorization) {
        log.info("보험목록조회 보험사 연동 - bankCodeStd: {}, userSeqNo: {}", request.getBankCodeStd(), request.getUserSeqNo());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            log.info("User CI 변환 완료 - userSeqNo: {} → userCi: {}", request.getUserSeqNo(), userCi);
            
            String insuranceCompanyUrl = getInsuranceCompanyUrl(request.getBankCodeStd());
            String apiUrl = insuranceCompanyUrl + "/v2.0/insurances";
            
            // 보험사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> insuranceCompanyRequest = new HashMap<>();
            insuranceCompanyRequest.put("bank_tran_id", request.getBankTranId());
            insuranceCompanyRequest.put("bank_code_std", request.getBankCodeStd());
            insuranceCompanyRequest.put("user_ci", userCi); // user_ci 사용
            insuranceCompanyRequest.put("befor_inquiry_trace_info", request.getBeforInquiryTraceInfo());
            insuranceCompanyRequest.put("search_timestamp", System.currentTimeMillis());
            
            log.info("보험사에 전송할 요청 데이터: {}", insuranceCompanyRequest);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(insuranceCompanyRequest, headers);
            
            ResponseEntity<InsuranceListResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    InsuranceListResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("보험목록조회 보험사 연동 성공 - userSeqNo: {}, insuCnt: {}", 
                        request.getUserSeqNo(), response.getBody().getInsuCnt());
                return response.getBody();
            } else {
                log.error("보험목록조회 보험사 연동 실패 - HTTP Status: {}, Response: {}", 
                         response.getStatusCode(), response.getBody());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("보험목록조회 보험사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 보험사 서버로 보험납입정보조회 요청
     */
    public InsurancePaymentResponse getInsurancePayment(InsurancePaymentRequest request, String authorization) {
        log.info("보험납입정보조회 보험사 연동 - bankCodeStd: {}, insuNum: {}", request.getBankCodeStd(), request.getInsuNum());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            
            String insuranceCompanyUrl = getInsuranceCompanyUrl(request.getBankCodeStd());
            String apiUrl = insuranceCompanyUrl + "/v2.0/insurances/payment";
            
            // 보험사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> insuranceCompanyRequest = new HashMap<>();
            insuranceCompanyRequest.put("bank_tran_id", request.getBankTranId());
            insuranceCompanyRequest.put("bank_code_std", request.getBankCodeStd());
            insuranceCompanyRequest.put("user_ci", userCi); // user_ci 사용
            insuranceCompanyRequest.put("insu_num", request.getInsuNum());
            insuranceCompanyRequest.put("search_timestamp", System.currentTimeMillis());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(insuranceCompanyRequest, headers);
            
            ResponseEntity<InsurancePaymentResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    InsurancePaymentResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("보험납입정보조회 보험사 연동 성공 - insuNum: {} (userCi: {})", request.getInsuNum(), userCi);
                return response.getBody();
            } else {
                log.error("보험납입정보조회 보험사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("보험납입정보조회 보험사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
} 