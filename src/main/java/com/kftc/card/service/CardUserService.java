package com.kftc.card.service;

import com.kftc.card.dto.CardUserRegisterRequest;
import com.kftc.card.dto.CardUserRegisterResponse;
import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * 카드사용자 등록
     */
    public CardUserRegisterResponse registerCardUser(CardUserRegisterRequest request, String authorization) {
        log.info("카드사용자등록 요청 - bankTranId: {}, userName: {}", request.getBankTranId(), request.getUserName());
        
        // 1. Authorization 헤더 검증
        validateAuthorization(authorization);
        
        // 2. 요청 데이터 검증
        validateRequest(request);
        
        // 3. 카드사용자 등록 처리 (카드사 서버 연동)
        CardUserRegisterResponse cardCompanyResponse = processCardUserRegistration(request, authorization);
        
        // 4. 카드사 응답을 오픈뱅킹 응답 형태로 변환하여 반환
        return transformToOpenBankingResponse(request, cardCompanyResponse);
    }
    
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
     * 요청 데이터 검증
     */
    private void validateRequest(CardUserRegisterRequest request) {
        // scope 검증 - cardinfo만 허용
        if (!"cardinfo".equals(request.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_SCOPE);
        }
        
        // 제3자정보제공동의여부 검증 - Y만 허용
        if (!"Y".equals(request.getInfoPrvdAgmtYn())) {
            throw new BusinessException(ErrorCode.INVALID_AGREEMENT);
        }
        
        log.debug("요청 데이터 검증 완료");
    }
    
    /**
     * 카드사용자 등록 처리
     */
    private CardUserRegisterResponse processCardUserRegistration(CardUserRegisterRequest request, String authorization) {
        log.info("카드사용자 등록 처리 시작 - bankCodeStd: {}, userCi: {}", request.getBankCodeStd(), request.getUserCi());
        
        try {
            // 1. 카드사 연동하여 유효한 카드 목록 확인
            boolean hasValidCards = cardCompanyService.hasValidCards(request.getBankCodeStd(), request.getUserCi());
            if (!hasValidCards) {
                log.warn("유효한 카드가 없는 사용자 - userCi: {}", request.getUserCi());
                throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
            }
            
            // 2. 카드사 서버로 사용자 등록 요청
            CardUserRegisterResponse cardCompanyResponse = cardCompanyService.registerUserToCardCompany(request, authorization);
            
            // 3. 카드사 응답 검증
            if (!"A0000".equals(cardCompanyResponse.getRspCode()) && !"A0324".equals(cardCompanyResponse.getRspCode())) {
                log.error("카드사 등록 실패 - rspCode: {}, rspMessage: {}", 
                         cardCompanyResponse.getRspCode(), cardCompanyResponse.getRspMessage());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            
            log.info("카드사용자 등록 완료 - userSeqNo: {}, rspCode: {}", 
                    cardCompanyResponse.getUserSeqNo(), cardCompanyResponse.getRspCode());
            
            return cardCompanyResponse;
            
        } catch (BusinessException e) {
            log.error("카드사용자 등록 처리 실패 - userCi: {}, error: {}", request.getUserCi(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("카드사용자 등록 처리 중 예외 발생 - userCi: {}, error: {}", request.getUserCi(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 카드사 응답을 오픈뱅킹 응답 형태로 변환
     */
    private CardUserRegisterResponse transformToOpenBankingResponse(CardUserRegisterRequest request, CardUserRegisterResponse cardCompanyResponse) {
        // 오픈뱅킹 API 응답 형태로 변환
        String apiTranId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        return CardUserRegisterResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode(cardCompanyResponse.getRspCode()) // 카드사 응답 코드 그대로 전달
                .rspMessage(cardCompanyResponse.getRspMessage())
                .bankTranId(request.getBankTranId())
                .bankTranDate(cardCompanyResponse.getBankTranDate())
                .bankCodeTran(cardCompanyResponse.getBankCodeTran())
                .bankRspCode(cardCompanyResponse.getBankRspCode())
                .bankRspMessage(cardCompanyResponse.getBankRspMessage())
                .bankName(cardCompanyResponse.getBankName())
                .userSeqNo(cardCompanyResponse.getUserSeqNo()) // 카드사에서 부여한 사용자 일련번호
                .build();
    }
} 