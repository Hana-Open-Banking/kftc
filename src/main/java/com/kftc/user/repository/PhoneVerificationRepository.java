package com.kftc.user.repository;

import com.kftc.user.entity.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
    
    Optional<PhoneVerification> findByPhoneNumberAndVerificationCodeAndVerifiedFalse(
            String phoneNumber, String verificationCode);
    
    Optional<PhoneVerification> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
    
    void deleteByPhoneNumber(String phoneNumber);
} 