package com.kftc.card.service;

import com.kftc.card.dto.CardUserRegisterRequest;
import com.kftc.card.dto.CardUserRegisterResponse;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardCompanyService {
    
    private final RestTemplate restTemplate;
    
    @Value("${card.company.kb.url:http://localhost:8081}")
    private String kbCardServerUrl;
    
    /**
     * 카드사 서버로 카드사용자 등록 요청
     */
    public CardUserRegisterResponse registerUserToCardCompany(CardUserRegisterRequest request, String authorization) {
        log.info("카드사 연동 요청 시작 - bankCodeStd: {}, userCi: {}", request.getBankCodeStd(), request.getUserCi());
        
        try {
            // 카드사별 서버 URL 결정
            String cardCompanyUrl = getCardCompanyUrl(request.getBankCodeStd());
            String apiUrl = cardCompanyUrl + "/v2.0/user/register_card";
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorization);
            
            // HTTP 요청 생성
            HttpEntity<CardUserRegisterRequest> requestEntity = new HttpEntity<>(request, headers);
            
            // 카드사 서버 호출
            ResponseEntity<CardUserRegisterResponse> response = restTemplate.exchange(
                    apiUrl, 
                    HttpMethod.POST, 
                    requestEntity, 
                    CardUserRegisterResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                CardUserRegisterResponse cardCompanyResponse = response.getBody();
                log.info("카드사 연동 응답 성공 - userSeqNo: {}, rspCode: {}", 
                        cardCompanyResponse.getUserSeqNo(), cardCompanyResponse.getRspCode());
                return cardCompanyResponse;
            } else {
                log.error("카드사 연동 실패 - HTTP Status: {}", response.getStatusCode());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
        } catch (Exception e) {
            log.error("카드사 연동 중 오류 발생 - bankCodeStd: {}, error: {}", request.getBankCodeStd(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 카드사 코드에 따른 서버 URL 반환
     */
    private String getCardCompanyUrl(String bankCodeStd) {
        return switch (bankCodeStd) {
            case "091" -> kbCardServerUrl; // KB카드
            // 다른 카드사들도 추가 가능
            case "092" -> "http://localhost:8082"; // 예: 삼성카드
            case "093" -> "http://localhost:8083"; // 예: 현대카드
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
} 