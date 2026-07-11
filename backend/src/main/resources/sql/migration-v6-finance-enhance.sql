-- ============================================================
-- Phase 10 - Finance Sync Enhancement
-- 为 finance_sync_record 表补充字段，支持完整的资产价值快照
-- 原因：原表仅有 sync_month/total_amount/record_count/status/remark/created_at，
-- 缺少批次号、资产数量、原值/净值/累计折旧快照、本月折旧额、操作人等字段，
-- 无法满足财务分析与数据同步的完整数据记录需求。
-- ============================================================

USE fixed_asset_lifecycle_system;
SET NAMES utf8mb4;

-- 幂等：使用存储过程检查列是否存在，避免重复执行报错
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl_name VARCHAR(64),
    IN col_name VARCHAR(64),
    IN col_def  TEXT,
    IN after_col VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'fixed_asset_lifecycle_system'
          AND TABLE_NAME = tbl_name
          AND COLUMN_NAME = col_name
    ) THEN
        SET @stmt = CONCAT('ALTER TABLE ', tbl_name, ' ADD COLUMN ', col_name, ' ', col_def, ' AFTER ', after_col);
        PREPARE stmt FROM @stmt;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_column_if_not_exists('finance_sync_record', 'sync_batch_no',              'VARCHAR(64) DEFAULT NULL COMMENT ''同步批次号''',          'sync_month');
CALL add_column_if_not_exists('finance_sync_record', 'asset_count',                'INT NOT NULL DEFAULT 0 COMMENT ''资产数量''',              'record_count');
CALL add_column_if_not_exists('finance_sync_record', 'total_original_value',       'DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''原值总额''',    'total_amount');
CALL add_column_if_not_exists('finance_sync_record', 'total_net_value',            'DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''净值总额''',    'total_original_value');
CALL add_column_if_not_exists('finance_sync_record', 'total_accumulated_depreciation', 'DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''累计折旧总额''', 'total_net_value');
CALL add_column_if_not_exists('finance_sync_record', 'monthly_depreciation',       'DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''本月折旧额''',  'total_accumulated_depreciation');
CALL add_column_if_not_exists('finance_sync_record', 'operator_name',              'VARCHAR(64) DEFAULT NULL COMMENT ''操作人''',              'status');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
