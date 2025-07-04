package com.kftc.card.service;

import com.kftc.card.dto.*;
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
public class CardCompanyService {
    
    private final RestTemplate restTemplate;
    private final UserService userService;
    
    @Value("${financial.institutions.kookmin-card.base-url}")
    private String kbCardServerUrl;
    
    /**
     * 카드사 코드에 따른 서버 URL 반환
     */
    private String getCardCompanyUrl(String bankCodeStd) {
        return switch (bankCodeStd) {
            case "381" -> kbCardServerUrl; // KB카드
            // 다른 카드사들도 추가 가능
            default -> {
                log.error("지원하지 않는 카드사 코드: {}", bankCodeStd);
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        };
    }
    
    /**
     * 카드사별 유효한 카드 목록 확인
     */
    public boolean hasValidCards(String bankCodeStd, String userCi) {
        // 실제로는 카드사별 API를 호출하여 유효한 카드 목록을 확인해야 함
        // 여기서는 기본적으로 true 반환 (카드사 서버에서 최종 검증)
        log.debug("카드 유효성 확인 - bankCodeStd: {}, userCi: {}", bankCodeStd, userCi);
        return true;
    }

    /**
     * 카드사 서버로 카드목록조회 요청
     */
    public CardListResponse getCardList(CardListRequest request, String authorization) {
        log.info("카드목록조회 카드사 연동 - bankCodeStd: {}, userSeqNo: {}", request.getBankCodeStd(), request.getUserSeqNo());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            log.info("User CI 변환 완료 - userSeqNo: {} → userCi: {}", request.getUserSeqNo(), userCi);
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/list";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            cardCompanyRequest.put("beforInquiryTraceInfo", request.getBeforInquiryTraceInfo());
            
            log.info("카드사에 전송할 요청 데이터: {}", cardCompanyRequest);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardListResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardListResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                CardListResponse cardListResponse = response.getBody();
                
                // 카드사 응답의 cardImage 필드를 KFTC 응답에 포함
                if (cardListResponse.getCardList() != null) {
                    cardListResponse.getCardList().forEach(cardInfo -> {
                        // cardImage 필드가 이미 포함되어 있으므로 추가 처리 필요 없음
                        log.debug("카드 이미지 정보 포함 - cardId: {}, cardImage: {}", 
                                cardInfo.getCardId(), cardInfo.getCardImage());
                    });
                }
                
                return cardListResponse;
            } else {
                log.error("카드사 서버 응답 실패 - status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드목록조회 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 카드사 서버로 카드조회해지 요청
     */
    public CardCancelResponse cancelCardInquiry(CardCancelRequest request, String authorization) {
        log.info("카드조회해지 카드사 연동 - bankCodeStd: {}, userSeqNo: {}", request.getBankCodeStd(), request.getUserSeqNo());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/cancel";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardCancelResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardCancelResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("카드조회해지 카드사 연동 성공 - userSeqNo: {} (userCi: {})", request.getUserSeqNo(), userCi);
                return response.getBody();
            } else {
                log.error("카드조회해지 카드사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드조회해지 카드사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 카드사 서버로 카드기본정보조회 요청
     */
    public CardIssueInfoResponse getCardIssueInfo(CardIssueInfoRequest request, String authorization) {
        log.info("카드기본정보조회 카드사 연동 - bankCodeStd: {}, cardId: {}", request.getBankCodeStd(), request.getCardId());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/issue_info";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            cardCompanyRequest.put("cardId", request.getCardId());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardIssueInfoResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardIssueInfoResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("카드기본정보조회 카드사 연동 성공 - cardId: {} (userCi: {})", request.getCardId(), userCi);
                return response.getBody();
            } else {
                log.error("카드기본정보조회 카드사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드기본정보조회 카드사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 카드사 서버로 카드청구기본정보조회 요청
     */
    public CardBillsResponse getCardBills(CardBillsRequest request, String authorization) {
        log.info("카드청구기본정보조회 카드사 연동 - bankCodeStd: {}, fromMonth: {}, toMonth: {}", 
                request.getBankCodeStd(), request.getFromMonth(), request.getToMonth());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/bills";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            cardCompanyRequest.put("fromMonth", request.getFromMonth());
            cardCompanyRequest.put("toMonth", request.getToMonth());
            cardCompanyRequest.put("beforInquiryTraceInfo", request.getBeforInquiryTraceInfo());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardBillsResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardBillsResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("카드청구기본정보조회 카드사 연동 성공 - billCnt: {} (userCi: {})", response.getBody().getBillCnt(), userCi);
                return response.getBody();
            } else {
                log.error("카드청구기본정보조회 카드사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드청구기본정보조회 카드사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 카드사 서버로 카드청구상세정보조회 요청
     */
    public CardBillDetailResponse getCardBillDetail(CardBillDetailRequest request, String authorization) {
        log.info("카드청구상세정보조회 카드사 연동 - bankCodeStd: {}, chargeMonth: {}, settlementSeqNo: {}", 
                request.getBankCodeStd(), request.getChargeMonth(), request.getSettlementSeqNo());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/bills/detail";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            cardCompanyRequest.put("chargeMonth", request.getChargeMonth());
            cardCompanyRequest.put("settlementSeqNo", request.getSettlementSeqNo());
            cardCompanyRequest.put("beforInquiryTraceInfo", request.getBeforInquiryTraceInfo());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardBillDetailResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardBillDetailResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("카드청구상세정보조회 카드사 연동 성공 - billDetailCnt: {} (userCi: {})", response.getBody().getBillDetailCnt(), userCi);
                return response.getBody();
            } else {
                log.error("카드청구상세정보조회 카드사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드청구상세정보조회 카드사 연동 중 오류 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 카드사 서버로 카드거래내역조회 요청
     */
    public CardTransactionResponse getCardTransactions(CardTransactionRequest request, String authorization) {
        log.info("카드거래내역조회 카드사 연동 - bankCodeStd: {}, cardId: {}, fromDate: {}, toDate: {}", 
                request.getBankCodeStd(), request.getCardId(), request.getFromDate(), request.getToDate());
        
        try {
            // user_seq_no를 user_ci로 변환
            String userCi = userService.getUserCiByUserSeqNo(request.getUserSeqNo());
            log.info("User CI 변환 완료 - userSeqNo: {} → userCi: {}", request.getUserSeqNo(), userCi);
            
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/cards/transactions";
            
            // 카드사용 요청 데이터 생성 (user_ci로 변환)
            Map<String, Object> cardCompanyRequest = new HashMap<>();
            cardCompanyRequest.put("bankTranId", request.getBankTranId());
            cardCompanyRequest.put("bankCodeStd", request.getBankCodeStd());
            cardCompanyRequest.put("memberBankCode", request.getMemberBankCode());
            cardCompanyRequest.put("userCi", userCi); // user_ci 사용
            cardCompanyRequest.put("cardId", request.getCardId());
            cardCompanyRequest.put("fromDate", request.getFromDate());
            cardCompanyRequest.put("toDate", request.getToDate());
            cardCompanyRequest.put("pageIndex", request.getPageIndex());
            cardCompanyRequest.put("beforInquiryTraceInfo", request.getBeforInquiryTraceInfo());
            
            log.info("카드사에 전송할 거래내역조회 요청 데이터: {}", cardCompanyRequest);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(cardCompanyRequest, headers);
            
            ResponseEntity<CardTransactionResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardTransactionResponse.class
            );
            
            log.info("카드거래내역조회 카드사 응답 상태: {}", response.getStatusCode());
            log.info("카드거래내역조회 카드사 응답 본문: {}", response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("카드거래내역조회 카드사 연동 성공 - cardId: {}, tranCnt: {}", 
                        request.getCardId(), response.getBody().getTranCnt());
                return response.getBody();
            } else {
                log.error("카드거래내역조회 카드사 연동 실패 - HTTP Status: {}, Response: {}", 
                         response.getStatusCode(), response.getBody());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드거래내역조회 카드사 연동 중 오류 - bankCodeStd: {}, cardId: {}, error: {}", 
                     request.getBankCodeStd(), request.getCardId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
} 