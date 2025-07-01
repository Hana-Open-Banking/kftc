package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.EntityNotFoundException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.dto.KftcTokenResponse;

import com.kftc.user.dto.UserRegisterResponse;
import com.kftc.user.entity.User;
import com.kftc.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final KftcInternalService kftcInternalService;
    
    /**
     * CI로 사용자 생성 또는 조회 (오픈뱅킹 플로우용)  
     */
    @Transactional
    public String createOrGetUserByCi(String ci) {
        return createOrGetUserByCi(ci, null, null);
    }
    
    /**
     * CI로 사용자 생성 또는 조회 - 이름과 이메일 포함 (PASS 인증용)
     */
    @Transactional
    public String createOrGetUserByCi(String ci, String userName, String userEmail) {
        log.info("CI로 사용자 생성/조회: ci={}, userName={}, userEmail={}", 
                ci.substring(0, 10) + "...", userName, userEmail);
        
        // 기존 사용자 조회
        Optional<User> existingUser = userRepository.findByUserCi(ci);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("기존 사용자 발견: userSeqNo={}", user.getUserSeqNo());
            
            // 기존 사용자가 있으면 이름과 이메일 정보 업데이트
            if (userName != null || userEmail != null) {
                User updatedUser = User.builder()
                        .userSeqNo(user.getUserSeqNo())
                        .userCi(user.getUserCi())
                        .userName(userName != null ? userName : user.getUserName())
                        .userType(user.getUserType())
                        .userStatus(user.getUserStatus())
                        .userEmail(userEmail != null ? userEmail : user.getUserEmail())
                        .userInfo(user.getUserInfo())
                        .build();
                
                userRepository.save(updatedUser);
                log.info("기존 사용자 정보 업데이트 완료: userSeqNo={}", user.getUserSeqNo());
            }
            
            return user.getUserSeqNo();
        }
        
        // 새 사용자 생성
        String userSeqNo = generateNextUserSeqNo();
        User newUser = User.builder()
                .userSeqNo(userSeqNo)
                .userCi(ci)
                .userName(userName != null ? userName : "사용자" + userSeqNo)
                .userType("PERSONAL")
                .userStatus("PENDING") // 동의 전이므로 PENDING 상태
                .userEmail(userEmail)
                .userInfo(null)
                .build();
        
        User savedUser = userRepository.save(newUser);
        log.info("새 사용자 생성 완료: userSeqNo={}, userName={}, userEmail={}, ci={}", 
                savedUser.getUserSeqNo(), savedUser.getUserName(), savedUser.getUserEmail(), 
                ci.substring(0, 10) + "...");
        
        return savedUser.getUserSeqNo();
    }
    
    /**
     * 사용자 조회 메서드들
     */
    @Transactional(readOnly = true)
    public User findByCi(String ci) {
        return userRepository.findByUserCi(ci)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 CI의 사용자를 찾을 수 없습니다."));
    }
    
    @Transactional(readOnly = true)
    public User findByUserSeqNo(String userSeqNo) {
        return userRepository.findByUserSeqNo(userSeqNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 사용자 일련번호의 사용자를 찾을 수 없습니다."));
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByCi(String ci) {
        return userRepository.findByUserCiAndUserStatus(ci, "ACTIVE");
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByUserSeqNo(String userSeqNo) {
        return userRepository.findByUserSeqNoAndUserStatus(userSeqNo, "ACTIVE");
    }
    
    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public long getTotalActiveUserCount() {
        return userRepository.countByUserStatus("ACTIVE");
    }
    
    @Transactional(readOnly = true)
    public long getPersonalUserCount() {
        return userRepository.countByUserTypeAndUserStatus("PERSONAL", "ACTIVE");
    }
    
    @Transactional(readOnly = true)
    public long getCorporateUserCount() {
        return userRepository.countByUserTypeAndUserStatus("CORPORATE", "ACTIVE");
    }
    
    /**
     * 유틸리티 메서드들
     */
    private void handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String message = e.getMessage();
        if (message != null && message.contains("user_ci")) {
            throw new BusinessException(ErrorCode.DUPLICATED_PHONE_NUMBER, "이미 가입된 사용자입니다.");
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 저장 중 오류가 발생했습니다.");
    }
    
    private String generateNextUserSeqNo() {
        Optional<String> maxUserSeqNo = userRepository.findMaxUserSeqNo();
        
        if (maxUserSeqNo.isPresent()) {
            long nextSeqNo = Long.parseLong(maxUserSeqNo.get()) + 1;
            return String.format("%010d", nextSeqNo);
        } else {
            return "1000000001"; // 시작 번호
        }
    }
    

    /**
     * KFTC 콜백 처리
     */
    @Transactional
    public UserRegisterResponse handleKftcCallback(String code, String state) {
        try {
            // state는 userId로 사용됨
            String userId = state;
            
            // 토큰 요청
            KftcTokenResponse tokenResponse = kftcInternalService.getAccessToken(code);
            
            User user = userRepository.findByUserSeqNo(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            
            // KFTC 토큰 업데이트
            user.updateUserInfo(user.getUserName(), user.getUserEmail(), tokenResponse.getAccessToken());
            userRepository.save(user);
            
            log.info("KFTC 콜백 처리 완료: userSeqNo={}", tokenResponse.getUserSeqNo());
            
            return UserRegisterResponse.builder()
                    .userSeqNo(user.getUserSeqNo())
                    .name(user.getUserName())
                    .phoneNumber(user.getUserEmail()) // 임시로 email 필드 사용
                    .ci(user.getUserCi())
                    .accessToken(user.getUserInfo())
                    .build();
                    
        } catch (Exception e) {
            log.error("KFTC 콜백 처리 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "KFTC 콜백 처리에 실패했습니다");
        }
    }
    

    /**
     * 사용자 동의 처리 - 상태를 ACTIVE로 변경
     */
    @Transactional
    public void activateUserConsent(String userSeqNo) {
        log.info("사용자 동의 처리: userSeqNo={}", userSeqNo);
        
        User user = userRepository.findByUserSeqNo(userSeqNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다: " + userSeqNo));
        
        // 상태를 ACTIVE로 변경
        User updatedUser = User.builder()
                .userSeqNo(user.getUserSeqNo())
                .userCi(user.getUserCi())
                .userName(user.getUserName())
                .userType(user.getUserType())
                .userStatus("ACTIVE") // PENDING → ACTIVE
                .userEmail(user.getUserEmail())
                .userInfo(user.getUserInfo())
                .build();
        
        userRepository.save(updatedUser);
        log.info("사용자 상태 활성화 완료: userSeqNo={}", userSeqNo);
    }
} 