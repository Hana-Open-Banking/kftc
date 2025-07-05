package com.kftc.insurance.service;

import com.kftc.insurance.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceService {
    
    private final InsuranceCompanyService insuranceCompanyService;
    
    /**
     * Authorization 헤더 검증
     */
    private void validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.error("유효하지 않은 Authorization 헤더: {}", authorization);
            throw new RuntimeException("Invalid Authorization header");
        }
        
        String token = authorization.substring(7);
        if (token.trim().isEmpty()) {
            log.error("Authorization 토큰이 비어있음");
            throw new RuntimeException("Empty Authorization token");
        }
        
        log.debug("Authorization 토큰 검증 완료");
    }
    
    /**
     * 보험목록조회
     */
    public InsuranceListResponse getInsuranceList(InsuranceListRequest request, String authorization) {
        log.info("보험목록조회 요청 - bankTranId: {}, userSeqNo: {}, bankCodeStd: {}", 
                request.getBankTranId(), request.getUserSeqNo(), request.getBankCodeStd());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 보험사 서버로 보험목록조회 요청
        InsuranceListResponse insuranceCompanyResponse = insuranceCompanyService.getInsuranceList(request, authorization);
        
        // 3. 보험사 응답을 오픈뱅킹 형식으로 변환
        return transformToInsuranceListResponse(request, insuranceCompanyResponse);
    }
    
    /**
     * 보험납입정보조회
     */
    public InsurancePaymentResponse getInsurancePayment(InsurancePaymentRequest request, String authorization) {
        log.info("보험납입정보조회 요청 - bankTranId: {}, userSeqNo: {}, insuNum: {}", 
                request.getBankTranId(), request.getUserSeqNo(), request.getInsuNum());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 보험사 서버로 보험납입정보조회 요청
        InsurancePaymentResponse insuranceCompanyResponse = insuranceCompanyService.getInsurancePayment(request, authorization);
        
        // 3. 보험사 응답을 오픈뱅킹 형식으로 변환
        return transformToInsurancePaymentResponse(request, insuranceCompanyResponse);
    }
    
    /**
     * 보험목록조회 응답 변환
     */
    private InsuranceListResponse transformToInsuranceListResponse(InsuranceListRequest request, InsuranceListResponse insuranceCompanyResponse) {
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        return InsuranceListResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode(insuranceCompanyResponse.getRspCode())
                .rspMessage(insuranceCompanyResponse.getRspMessage())
                .bankTranId(request.getBankTranId())
                .bankTranDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .bankCodeTran("097") // 금융결제원 코드
                .bankRspCode(insuranceCompanyResponse.getBankRspCode())
                .bankRspMessage(insuranceCompanyResponse.getBankRspMessage())
                .userSeqNo(request.getUserSeqNo())
                .nextPageYn(insuranceCompanyResponse.getNextPageYn())
                .beforInquiryTraceInfo(insuranceCompanyResponse.getBeforInquiryTraceInfo())
                .insuList(insuranceCompanyResponse.getInsuList())
                .insuCnt(insuranceCompanyResponse.getInsuCnt())
                .build();
    }
    
    /**
     * 보험납입정보조회 응답 변환
     */
    private InsurancePaymentResponse transformToInsurancePaymentResponse(InsurancePaymentRequest request, InsurancePaymentResponse insuranceCompanyResponse) {
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        return InsurancePaymentResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode(insuranceCompanyResponse.getRspCode())
                .rspMessage(insuranceCompanyResponse.getRspMessage())
                .bankTranId(request.getBankTranId())
                .bankTranDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .bankCodeTran("097") // 금융결제원 코드
                .bankRspCode(insuranceCompanyResponse.getBankRspCode())
                .bankRspMessage(insuranceCompanyResponse.getBankRspMessage())
                .userSeqNo(request.getUserSeqNo())
                .payDue(insuranceCompanyResponse.getPayDue())
                .payCycle(insuranceCompanyResponse.getPayCycle())
                .payDate(insuranceCompanyResponse.getPayDate())
                .payEndDate(insuranceCompanyResponse.getPayEndDate())
                .payAmt(insuranceCompanyResponse.getPayAmt())
                .payOrgCode(insuranceCompanyResponse.getPayOrgCode())
                .payAccountNum(insuranceCompanyResponse.getPayAccountNum())
                .payAccountNumMasked(insuranceCompanyResponse.getPayAccountNumMasked())
                .build();
    }
} 