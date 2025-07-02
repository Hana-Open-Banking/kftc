package com.kftc.common.repository;

import com.kftc.common.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    
    /**
     * 특정 날짜에 거래고유번호 존재 여부 확인
     * 하루 동안 유일성 보장을 위한 중복 검사
     */
    boolean existsByTransactionDateAndTransactionId(LocalDate transactionDate, String transactionId);
    
    /**
     * 특정 날짜의 거래고유번호 조회
     */
    Optional<TransactionLog> findByTransactionDateAndTransactionId(LocalDate transactionDate, String transactionId);
    
    /**
     * 특정 날짜의 모든 거래 로그 조회
     */
    List<TransactionLog> findByTransactionDateOrderByCreatedAtDesc(LocalDate transactionDate);
    
    /**
     * 특정 날짜의 API별 거래 수 조회
     */
    @Query("SELECT tl.apiName, COUNT(tl) FROM TransactionLog tl " +
           "WHERE tl.transactionDate = :transactionDate " +
           "GROUP BY tl.apiName " +
           "ORDER BY COUNT(tl) DESC")
    List<Object[]> countByApiNameAndTransactionDate(@Param("transactionDate") LocalDate transactionDate);
    
    /**
     * 특정 사용자의 거래 로그 조회 (최근 순)
     */
    List<TransactionLog> findByUserSeqNoOrderByCreatedAtDesc(String userSeqNo);
    
    /**
     * 성공한 거래만 조회
     */
    List<TransactionLog> findByTransactionStatusOrderByCreatedAtDesc(TransactionLog.TransactionStatus status);
    
    /**
     * 오래된 거래 로그 삭제용 (예: 30일 이전)
     */
    void deleteByTransactionDateBefore(LocalDate cutoffDate);
    
    /**
     * 특정 기간의 거래 통계 조회
     */
    @Query("SELECT DATE(tl.createdAt) as date, " +
           "COUNT(tl) as totalCount, " +
           "SUM(CASE WHEN tl.transactionStatus = 'SUCCESS' THEN 1 ELSE 0 END) as successCount, " +
           "AVG(tl.processingTimeMs) as avgProcessingTime " +
           "FROM TransactionLog tl " +
           "WHERE tl.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(tl.createdAt) " +
           "ORDER BY DATE(tl.createdAt) DESC")
    List<Object[]> getTransactionStatistics(@Param("startDate") LocalDate startDate, 
                                          @Param("endDate") LocalDate endDate);
} 