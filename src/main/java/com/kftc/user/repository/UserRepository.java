package com.kftc.user.repository;

import com.kftc.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 휴대폰번호 관련
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    
    // CI 관련
    Optional<User> findByCi(String ci);
    boolean existsByCi(String ci);
    Optional<User> findByCiAndUserStatus(String ci, User.UserStatus userStatus);
    
    // 사용자 일련번호 관련
    Optional<User> findByUserSeqNum(String userSeqNum);
    boolean existsByUserSeqNum(String userSeqNum);
    Optional<User> findByUserSeqNumAndUserStatus(String userSeqNum, User.UserStatus userStatus);
    
    // 이메일 관련
    Optional<User> findByEmail(String email);
    
    // 통계 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.userType = :userType AND u.userStatus = :userStatus")
    long countByUserTypeAndUserStatus(@Param("userType") User.UserType userType, 
                                     @Param("userStatus") User.UserStatus userStatus);
    
    // 다음 사용자 일련번호 생성을 위한 최대값 조회
    @Query("SELECT MAX(u.userSeqNum) FROM User u WHERE u.userSeqNum IS NOT NULL")
    Optional<String> findMaxUserSeqNum();
    
    // 활성 사용자 조회
    @Query("SELECT u FROM User u WHERE u.userStatus = :status")
    java.util.List<User> findByUserStatus(@Param("status") User.UserStatus status);
    
    // 활성 사용자 수 조회
    long countByUserStatus(User.UserStatus userStatus);
} 