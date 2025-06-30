package com.kftc.user.repository;

import com.kftc.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // 휴대폰번호 관련 (임시로 userEmail 필드 사용)
    // Optional<User> findByPhoneNumber(String phoneNumber);
    // boolean existsByPhoneNumber(String phoneNumber);
    
    // CI 관련
    Optional<User> findByUserCi(String userCi);
    boolean existsByUserCi(String userCi);
    Optional<User> findByUserCiAndUserStatus(String userCi, String userStatus);
    
    // 사용자 일련번호 관련
    Optional<User> findByUserSeqNo(String userSeqNo);
    boolean existsByUserSeqNo(String userSeqNo);
    Optional<User> findByUserSeqNoAndUserStatus(String userSeqNo, String userStatus);
    
    // 이메일 관련
    Optional<User> findByUserEmail(String userEmail);
    
    // 통계 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.userType = :userType AND u.userStatus = :userStatus")
    long countByUserTypeAndUserStatus(@Param("userType") String userType, 
                                     @Param("userStatus") String userStatus);
    
    // 다음 사용자 일련번호 생성을 위한 최대값 조회
    @Query("SELECT MAX(u.userSeqNo) FROM User u WHERE u.userSeqNo IS NOT NULL")
    Optional<String> findMaxUserSeqNo();
    
    // 활성 사용자 조회
    @Query("SELECT u FROM User u WHERE u.userStatus = :status")
    java.util.List<User> findByUserStatus(@Param("status") String status);
    
    // 활성 사용자 수 조회
    long countByUserStatus(String userStatus);
} 