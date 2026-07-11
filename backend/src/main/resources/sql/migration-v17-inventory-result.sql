USE fixed_asset_lifecycle_system;

-- 盘点结果明细表：支持一个盘点记录有多个盘点结果
CREATE TABLE IF NOT EXISTS inventory_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    record_id BIGINT NOT NULL COMMENT '盘点记录ID',
    result_type VARCHAR(32) NOT NULL COMMENT '结果类型: NORMAL/LOCATION_MISMATCH/KEEPER_MISMATCH/MISSING',
    expected_value VARCHAR(255) DEFAULT NULL COMMENT '期望值',
    actual_value VARCHAR(255) DEFAULT NULL COMMENT '实际值',
    INDEX idx_inventory_result_record_id (record_id)
) COMMENT='盘点结果明细表';