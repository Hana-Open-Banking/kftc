package com.kftc.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import oracle.security.pki.OraclePKIProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.net.URL;
import java.security.Security;
import java.util.Properties;

@Slf4j
@Configuration
public class OracleCloudConfig {

    @Value("${oracle.cloud.jdbc-url}")
    private String jdbcUrl;
    
    @Value("${oracle.cloud.username}")
    private String username;
    
    @Value("${oracle.cloud.password}")
    private String password;
    
    @Value("${oracle.cloud.driver-class-name}")
    private String driverClassName;
    
    @Value("${oracle.cloud.maximum-pool-size}")
    private int maximumPoolSize;

    @Bean
    @Primary
    public DataSource dataSource() {
        try {
            log.info("Oracle Cloud DataSource 생성 시작...");

            // 1. Oracle PKI Provider 추가 (가장 중요!)
            try {
                Security.addProvider(new OraclePKIProvider());
                log.info("Oracle PKI Provider 추가 완료");
            } catch (Exception e) {
                log.warn("Oracle PKI Provider 추가 실패 (계속 진행): {}", e.getMessage());
            }

            // 2. Oracle JDBC 드라이버 로드
            Class.forName(driverClassName);
            log.info("Oracle JDBC 드라이버 로드 완료");

            // 3. 모든 Oracle 관련 시스템 속성 완전히 클리어
            clearAllOracleProperties();

            // 4. Wallet 디렉토리 설정
            URL walletUrl = OracleCloudConfig.class.getClassLoader().getResource("Wallet_DinkDB");
            if (walletUrl == null) {
                log.error("Wallet 디렉토리를 찾을 수 없습니다.");
                throw new RuntimeException("Wallet 디렉토리를 찾을 수 없습니다.");
            }

            File walletDir = new File(walletUrl.toURI());
            String walletPath = walletDir.getAbsolutePath();
            log.info("Oracle Cloud Wallet 경로: {}", walletPath);

            // 5. Wallet 파일들 확인
            checkWalletFiles(walletPath);

            // 6. 방법 1: cwallet.sso 사용 (가장 간단)
            if (new File(walletPath, "cwallet.sso").exists()) {
                log.info("cwallet.sso 파일 발견 - SSO Wallet 방식 시도");
                return createDataSourceWithSSO(walletPath);
            }
            // 7. 방법 2: JKS 파일 사용
            else if (new File(walletPath, "keystore.jks").exists()) {
                log.info("keystore.jks 파일 발견 - JKS 방식 시도");
                return createDataSourceWithJKS(walletPath);
            }
            // 8. 방법 3: 기본 TNS 방식
            else {
                log.info("기본 TNS 방식 시도");
                return createDataSourceWithTNS(walletPath);
            }

        } catch (Exception e) {
            log.error("Oracle Cloud DataSource 생성 실패: ", e);
            throw new RuntimeException("DataSource 생성 실패", e);
        }
    }

    private void clearAllOracleProperties() {
        String[] props = {
                "oracle.net.tns_admin", "oracle.net.wallet_location",
                "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType",
                "javax.net.ssl.keyStore", "javax.net.ssl.keyStorePassword", "javax.net.ssl.keyStoreType",
                "oracle.net.ssl_server_dn_match", "oracle.net.ssl_version", "oracle.net.ssl_cipher_suites",
                "oracle.net.authentication_services"
        };

        for (String prop : props) {
            System.clearProperty(prop);
        }
        log.info("모든 Oracle 시스템 속성 클리어 완료");
    }

    private void checkWalletFiles(String walletPath) {
        String[] requiredFiles = {"tnsnames.ora", "sqlnet.ora", "cwallet.sso", "ewallet.p12", "keystore.jks", "truststore.jks"};
        for (String fileName : requiredFiles) {
            File file = new File(walletPath, fileName);
            log.info("{} 파일 존재: {}", fileName, file.exists());
        }
    }

    private DataSource createDataSourceWithSSO(String walletPath) throws Exception {
        log.info("SSO Wallet 방식으로 연결 시도");

        // SSO Wallet 설정
        System.setProperty("oracle.net.tns_admin", walletPath);
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");

        // SSO 관련 설정
        // System.setProperty("javax.net.ssl.trustStore", walletPath + "/cwallet.sso");
        // System.setProperty("javax.net.ssl.trustStoreType", "SSO");
        // System.setProperty("javax.net.ssl.keyStore", walletPath + "/cwallet.sso");
        // System.setProperty("javax.net.ssl.keyStoreType", "SSO");
        // System.setProperty("oracle.net.authentication_services", "(TCPS)");

        return createHikariDataSource();
    }

    private DataSource createDataSourceWithJKS(String walletPath) throws Exception {
        log.info("JKS 방식으로 연결 시도");

        // JKS 설정
        // System.setProperty("oracle.net.tns_admin", walletPath);
        // System.setProperty("javax.net.ssl.trustStore", walletPath + "/truststore.jks");
        // System.setProperty("javax.net.ssl.trustStorePassword", "Data2504");
        // System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        // System.setProperty("javax.net.ssl.keyStore", walletPath + "/keystore.jks");
        // System.setProperty("javax.net.ssl.keyStorePassword", "Data2504");
        // System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        // System.setProperty("oracle.net.ssl_server_dn_match", "true");

        return createHikariDataSource();
    }

    private DataSource createDataSourceWithTNS(String walletPath) throws Exception {
        log.info("기본 TNS 방식으로 연결 시도");

        // 최소한의 TNS 설정
        System.setProperty("oracle.net.tns_admin", walletPath);

        return createHikariDataSource();
    }

    private DataSource createHikariDataSource() throws Exception {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // 연결 풀 설정 - 관대하게
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(120000);  // 2분
        config.setIdleTimeout(600000);        // 10분
        config.setMaxLifetime(1800000);       // 30분
        config.setLeakDetectionThreshold(0);  // 비활성화
        config.setInitializationFailTimeout(-1); // 무한 대기

        // Oracle 연결 속성
        Properties props = new Properties();
        props.setProperty("oracle.jdbc.fanEnabled", "false");
        props.setProperty("oracle.jdbc.autoCommitSpecCompliant", "false");
        props.setProperty("oracle.jdbc.ReadTimeout", "120000");
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "120000");
        config.setDataSourceProperties(props);

        log.info("HikariDataSource 생성 시도...");
        HikariDataSource dataSource = new HikariDataSource(config);

        // 연결 테스트
        try {
            log.info("Oracle Cloud Database 연결 테스트...");
            try (var connection = dataSource.getConnection()) {
                log.info("Oracle Cloud Database 연결 테스트 성공!");
                log.info("DB 제품: {}", connection.getMetaData().getDatabaseProductName());
            }
        } catch (Exception testEx) {
            log.warn("연결 테스트 실패, 하지만 DataSource 반환: {}", testEx.getMessage());
        }

        return dataSource;
    }
}