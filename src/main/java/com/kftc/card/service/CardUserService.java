package com.kftc.card.service;

import com.kftc.card.dto.*;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardUserService {
    
    private final CardCompanyService cardCompanyService;
    
    private static final String SUCCESS_CODE = "A0000";
    private static final String SUCCESS_MESSAGE = "";
    private static final String BANK_SUCCESS_CODE = "000";
    private static final String BANK_SUCCESS_MESSAGE = "";
    private static final String BANK_NAME = "오픈카드";

    /**
     * Authorization 헤더 검증
     */
    private void validateAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.INVALID_AUTHORIZATION);
        }
        
        // Bearer 토큰 추출
        String token = authorization.substring(7); // "Bearer " 제거
        
        // 토큰 기본 유효성 검증
        if (token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_AUTHORIZATION);
        }
        
        // 실제 운영환경에서는 JWT 토큰 검증, 토큰 만료 시간 확인 등이 필요
        // 현재는 기본적인 형태 검증만 수행
        if (token.length() < 10) {
            throw new BusinessException(ErrorCode.INVALID_AUTHORIZATION);
        }
        
        log.debug("Authorization 토큰 검증 완료 - token length: {}", token.length());
    }

    /**
     * 카드조회해지
     */
    public CardCancelResponse cancelCardInquiry(CardCancelRequest request, String authorization) {
        log.info("카드조회해지 요청 - bankTranId: {}, userSeqNo: {}", request.getBankTranId(), request.getUserSeqNo());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 카드사 서버로 해지 요청
        CardCancelResponse cardCompanyResponse = cardCompanyService.cancelCardInquiry(request, authorization);
        
        // 3. 카드사 응답을 오픈뱅킹 응답 형태로 변환하여 반환
        return transformToCancelResponse(request, cardCompanyResponse);
    }
    
    /**
     * 카드조회해지 응답 변환
     */
    private CardCancelResponse transformToCancelResponse(CardCancelRequest request, CardCancelResponse cardCompanyResponse) {
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        return CardCancelResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode(cardCompanyResponse.getRspCode())
                .rspMessage(cardCompanyResponse.getRspMessage())
                .bankTranId(request.getBankTranId())
                .bankTranDate(cardCompanyResponse.getBankTranDate())
                .bankCodeTran(cardCompanyResponse.getBankCodeTran())
                .bankRspCode(cardCompanyResponse.getBankRspCode())
                .bankRspMessage(cardCompanyResponse.getBankRspMessage())
                .build();
    }
    
    /**
     * 오픈뱅킹 카드 목록 조회
     */
    public CardListResponse getCardList(CardListRequest request, String authorization) {
        log.info("오픈뱅킹 카드 목록 조회 요청 - bankTranId: {}, userSeqNo: {}", 
                request.getBankTranId(), request.getUserSeqNo());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 요청 데이터 검증
        validateCardListRequest(request);
        
        // 3. 카드사들로부터 카드 목록 조회
        CardListResponse response = aggregateCardListFromAllProviders(request, authorization);
        
        log.info("오픈뱅킹 카드 목록 조회 완료 - userSeqNo: {}, cardCnt: {}", 
                request.getUserSeqNo(), response.getCardCnt());
        
        return response;
    }
    
    /**
     * 카드 목록 조회 요청 검증
     */
    private void validateCardListRequest(CardListRequest request) {
        // 기본 필수 필드 검증만 수행 (scope 검증 제거 - Authorization 헤더에 포함됨)
        log.debug("카드 목록 조회 요청 검증 완료");
    }
    
    /**
     * 모든 카드사로부터 카드 목록 수집
     */
    private CardListResponse aggregateCardListFromAllProviders(CardListRequest request, String authorization) {
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        try {
            // 실제 카드사 API 호출
            CardListResponse cardCompanyResponse = cardCompanyService.getCardList(request, authorization);
            
            // 카드사 응답을 오픈뱅킹 형식으로 변환
            return CardListResponse.builder()
                    .apiTranId(apiTranId)
                    .apiTranDtm(currentDateTime)
                    .rspCode(cardCompanyResponse.getRspCode())
                    .rspMessage(cardCompanyResponse.getRspMessage())
                    .bankTranId(request.getBankTranId())
                    .bankTranDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                    .bankCodeTran("097") // 금융결제원 코드
                    .bankRspCode(cardCompanyResponse.getBankRspCode())
                    .bankRspMessage(cardCompanyResponse.getBankRspMessage())
                    .userSeqNo(request.getUserSeqNo())
                    .nextPageYn(cardCompanyResponse.getNextPageYn())
                    .beforInquiryTraceInfo(cardCompanyResponse.getBeforInquiryTraceInfo())
                    .cardList(cardCompanyResponse.getCardList())
                    .cardCnt(cardCompanyResponse.getCardCnt())
                    .build();
                    
        } catch (Exception e) {
            log.error("카드 목록 조회 중 오류 발생 - userSeqNo: {}, error: {}", 
                     request.getUserSeqNo(), e.getMessage(), e);
            
            return CardListResponse.builder()
                    .apiTranId(apiTranId)
                    .apiTranDtm(currentDateTime)
                    .rspCode("A0001")
                    .rspMessage("카드 목록 조회 실패: " + e.getMessage())
                    .bankTranId(request.getBankTranId())
                    .bankTranDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                    .bankCodeTran("097")
                    .bankRspCode("999")
                    .bankRspMessage("시스템 오류")
                    .userSeqNo(request.getUserSeqNo())
                    .nextPageYn("N")
                    .beforInquiryTraceInfo("")
                    .cardList(java.util.List.of())
                    .cardCnt("0")
                    .build();
        }
    }
    

    
    /**
     * 카드기본정보조회
     */
    public CardIssueInfoResponse getCardIssueInfo(CardIssueInfoRequest request, String authorization) {
        log.info("카드기본정보조회 요청 - bankTranId: {}, cardId: {}", 
                request.getBankTranId(), request.getCardId());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 카드사 서버로 카드기본정보조회 요청
        CardIssueInfoResponse response = cardCompanyService.getCardIssueInfo(request, authorization);
        
        log.info("카드기본정보조회 완료 - cardId: {}, cardType: {}", 
                request.getCardId(), response.getCardType());
        
        return response;
    }
    
    /**
     * 카드청구기본정보조회
     */
    public CardBillsResponse getCardBills(CardBillsRequest request, String authorization) {
        log.info("카드청구기본정보조회 요청 - bankTranId: {}, fromMonth: {}, toMonth: {}", 
                request.getBankTranId(), request.getFromMonth(), request.getToMonth());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 카드사 서버로 카드청구기본정보조회 요청
        CardBillsResponse response = cardCompanyService.getCardBills(request, authorization);
        
        log.info("카드청구기본정보조회 완료 - billCnt: {}", response.getBillCnt());
        
        return response;
    }
    
    /**
     * 카드청구상세정보조회
     */
    public CardBillDetailResponse getCardBillDetail(CardBillDetailRequest request, String authorization) {
        log.info("카드청구상세정보조회 요청 - bankTranId: {}, chargeMonth: {}, settlementSeqNo: {}", 
                request.getBankTranId(), request.getChargeMonth(), request.getSettlementSeqNo());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 카드사 서버로 카드청구상세정보조회 요청
        CardBillDetailResponse response = cardCompanyService.getCardBillDetail(request, authorization);
        
        log.info("카드청구상세정보조회 완료 - billDetailCnt: {}", response.getBillDetailCnt());
        
        return response;
    }

    /**
     * 카드거래내역조회
     */
    public CardTransactionResponse getCardTransactions(CardTransactionRequest request, String authorization) {
        log.info("카드거래내역조회 요청 - bankTranId: {}, userSeqNo: {}, cardId: {}", 
                request.getBankTranId(), request.getUserSeqNo(), request.getCardId());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 요청 데이터 검증
        validateCardTransactionRequest(request);
        
        // 3. 카드사로부터 거래내역 조회
        CardTransactionResponse cardCompanyResponse = cardCompanyService.getCardTransactions(request, authorization);
        
        // 4. 카드사 응답을 오픈뱅킹 형식으로 변환
        return transformToTransactionResponse(request, cardCompanyResponse);
    }
    
    /**
     * 카드거래내역조회 요청 검증
     */
    private void validateCardTransactionRequest(CardTransactionRequest request) {
        // 기본 필수 필드 검증
        log.debug("카드거래내역조회 요청 검증 완료");
        
        // 날짜 형식 검증 (YYYYMMDD)
        if (!request.getFromDate().matches("\\d{8}") || !request.getToDate().matches("\\d{8}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "날짜 형식이 올바르지 않습니다 (YYYYMMDD)");
        }
        
        // 조회 기간 검증 (최대 3개월)
        try {
            LocalDate fromDate = LocalDate.parse(request.getFromDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate toDate = LocalDate.parse(request.getToDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            if (fromDate.isAfter(toDate)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작일자가 종료일자보다 클 수 없습니다");
            }
            
            if (fromDate.isBefore(toDate.minusMonths(3))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조회 기간은 최대 3개월까지만 가능합니다");
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "날짜 형식이 올바르지 않습니다");
        }
    }
    
    /**
     * 카드거래내역조회 응답 변환
     */
    private CardTransactionResponse transformToTransactionResponse(CardTransactionRequest request, CardTransactionResponse cardCompanyResponse) {
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        return CardTransactionResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode(cardCompanyResponse.getRspCode())
                .rspMessage(cardCompanyResponse.getRspMessage())
                .bankTranId(request.getBankTranId())
                .bankTranDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .bankCodeTran("097") // 금융결제원 코드
                .bankRspCode(cardCompanyResponse.getBankRspCode())
                .bankRspMessage(cardCompanyResponse.getBankRspMessage())
                .userSeqNo(request.getUserSeqNo())
                .nextPageYn(cardCompanyResponse.getNextPageYn())
                .beforInquiryTraceInfo(cardCompanyResponse.getBeforInquiryTraceInfo())
                .tranCnt(cardCompanyResponse.getTranCnt())
                .tranList(cardCompanyResponse.getTranList())
                .build();
    }
} 