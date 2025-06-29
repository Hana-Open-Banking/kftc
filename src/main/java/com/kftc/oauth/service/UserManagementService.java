package com.kftc.oauth.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.oauth.domain.User;
import com.kftc.oauth.repository.OAuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {
    
    private final OAuthUserRepository userRepository;
    
    /**
     * 새로운 사용자 등록
     */
    public User registerUser(String userCi, User.UserType userType, String userName, 
                            String userEmail, String userBirthDate, String userCellNo, 
                            User.UserSexType userSexType) {
        
        // 중복 CI 확인
        if (userRepository.existsByUserCi(userCi)) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "이미 등록된 사용자입니다.");
        }
        
        // 새로운 사용자 일련번호 생성
        String userSeqNum = generateNextUserSeqNum();
        
        // 가입일자 생성
        String joinDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        User user = User.builder()
                .userSeqNum(userSeqNum)
                .userCi(userCi)
                .userType(userType)
                .userStatus(User.UserStatus.ACTIVE)
                .userName(userName)
                .userEmail(userEmail)
                .userBirthDate(userBirthDate)
                .userCellNo(userCellNo)
                .userSexType(userSexType)
                .joinDate(joinDate)
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("새로운 사용자 등록 완료: userSeqNum={}, userName={}", userSeqNum, userName);
        
        return savedUser;
    }
    
    /**
     * 사용자 일련번호로 활성 사용자 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> findActiveUser(String userSeqNum) {
        return userRepository.findByUserSeqNumAndUserStatus(userSeqNum, User.UserStatus.ACTIVE);
    }
    
    /**
     * 사용자 CI로 활성 사용자 조회
     */
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByCi(String userCi) {
        return userRepository.findByUserCiAndUserStatus(userCi, User.UserStatus.ACTIVE);
    }
    
    /**
     * 사용자 일련번호로 사용자 조회 (필수)
     */
    @Transactional(readOnly = true)
    public User getActiveUser(String userSeqNum) {
        return findActiveUser(userSeqNum)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, 
                        "활성 상태의 사용자를 찾을 수 없습니다: " + userSeqNum));
    }
    
    /**
     * 사용자 CI로 사용자 조회 또는 생성
     */
    public User findOrCreateUser(String userCi, String userName, String userEmail, 
                                String userBirthDate, String userCellNo, User.UserSexType userSexType) {
        
        // 기존 사용자 조회
        Optional<User> existingUser = findActiveUserByCi(userCi);
        if (existingUser.isPresent()) {
            log.info("기존 사용자 조회: userSeqNum={}", existingUser.get().getUserSeqNum());
            return existingUser.get();
        }
        
        // 새로운 사용자 생성
        log.info("새로운 사용자 생성 시작: userCi={}", userCi.substring(0, Math.min(10, userCi.length())) + "...");
        return registerUser(userCi, User.UserType.PERSONAL, userName, userEmail, 
                           userBirthDate, userCellNo, userSexType);
    }
    
    /**
     * 사용자 정보 업데이트
     */
    public User updateUser(String userSeqNum, String userName, String userEmail, String userCellNo) {
        User user = getActiveUser(userSeqNum);
        user.updateUserInfo(userName, userEmail, userCellNo);
        
        User updatedUser = userRepository.save(user);
        log.info("사용자 정보 업데이트 완료: userSeqNum={}", userSeqNum);
        
        return updatedUser;
    }
    
    /**
     * 사용자 상태 변경
     */
    public User updateUserStatus(String userSeqNum, User.UserStatus userStatus) {
        User user = getActiveUser(userSeqNum);
        user.updateStatus(userStatus);
        
        User updatedUser = userRepository.save(user);
        log.info("사용자 상태 변경 완료: userSeqNum={}, status={}", userSeqNum, userStatus);
        
        return updatedUser;
    }
    
    /**
     * 다음 사용자 일련번호 생성
     */
    private String generateNextUserSeqNum() {
        Optional<String> maxUserSeqNum = userRepository.findMaxUserSeqNum();
        
        if (maxUserSeqNum.isEmpty()) {
            // 첫 번째 사용자인 경우
            return "1000000001";
        }
        
        try {
            long currentMax = Long.parseLong(maxUserSeqNum.get());
            long nextSeqNum = currentMax + 1;
            
            // 10자리 형식으로 반환
            return String.format("%010d", nextSeqNum);
        } catch (NumberFormatException e) {
            log.error("사용자 일련번호 파싱 실패: {}", maxUserSeqNum.get());
            throw new BusinessException(ErrorCode.INVALID_VALUE, "사용자 일련번호 생성에 실패했습니다.");
        }
    }
    
    /**
     * 전체 사용자 수 조회
     */
    @Transactional(readOnly = true)
    public long getTotalActiveUserCount() {
        return userRepository.countByUserTypeAndUserStatus(User.UserType.PERSONAL, User.UserStatus.ACTIVE) +
               userRepository.countByUserTypeAndUserStatus(User.UserType.CORPORATE, User.UserStatus.ACTIVE);
    }
    
    /**
     * 개인 사용자 수 조회
     */
    @Transactional(readOnly = true)
    public long getPersonalUserCount() {
        return userRepository.countByUserTypeAndUserStatus(User.UserType.PERSONAL, User.UserStatus.ACTIVE);
    }
    
    /**
     * 법인 사용자 수 조회
     */
    @Transactional(readOnly = true)
    public long getCorporateUserCount() {
        return userRepository.countByUserTypeAndUserStatus(User.UserType.CORPORATE, User.UserStatus.ACTIVE);
    }
} 