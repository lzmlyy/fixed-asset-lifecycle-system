package com.example.asset.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataSourceInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DataSourceInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS inventory_result (
                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
                record_id BIGINT NOT NULL COMMENT '盘点记录ID',
                result_type VARCHAR(32) NOT NULL COMMENT '结果类型',
                expected_value VARCHAR(255) DEFAULT NULL COMMENT '期望值',
                actual_value VARCHAR(255) DEFAULT NULL COMMENT '实际值',
                INDEX idx_inventory_result_record_id (record_id)
            ) COMMENT='盘点结果明细表'
            """);
    }
}