package com.kftc.oauth.repository;

import com.kftc.oauth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthUserRepository extends JpaRepository<User, String> {
    
    /**
     * 사용자 일련번호로 활성 사용자 조회
     */
    Optional<User> findByUserSeqNumAndUserStatus(String userSeqNum, User.UserStatus userStatus);
    
    /**
     * 사용자 CI로 조회
     */
    Optional<User> findByUserCi(String userCi);
    
    /**
     * 사용자 CI로 활성 사용자 조회
     */
    Optional<User> findByUserCiAndUserStatus(String userCi, User.UserStatus userStatus);
    
    /**
     * 사용자 일련번호로 활성 사용자 존재 확인
     */
    boolean existsByUserSeqNumAndUserStatus(String userSeqNum, User.UserStatus userStatus);
    
    /**
     * 사용자 CI로 사용자 존재 확인
     */
    boolean existsByUserCi(String userCi);
    
    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByUserEmail(String userEmail);
    
    /**
     * 사용자 타입과 상태로 사용자 수 조회
     */
    @Query("SELECT COUNT(u) FROM OAuthUser u WHERE u.userType = :userType AND u.userStatus = :userStatus")
    long countByUserTypeAndUserStatus(@Param("userType") User.UserType userType, 
                                     @Param("userStatus") User.UserStatus userStatus);
    
    /**
     * 다음 사용자 일련번호 생성을 위한 최대값 조회
     */
    @Query("SELECT MAX(u.userSeqNum) FROM OAuthUser u")
    Optional<String> findMaxUserSeqNum();
} 