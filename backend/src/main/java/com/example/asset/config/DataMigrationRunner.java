package com.example.asset.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        try {
            int updated = jdbcTemplate.update(
                "INSERT IGNORE INTO sys_role_permission (role_id, permission_id) " +
                "SELECT r.id, p.id FROM sys_role r, sys_permission p " +
                "WHERE p.permission_code IN ('inventory:view', 'inventory:create') " +
                "AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id)"
            );
            if (updated > 0) {
                log.info("Granted inventory permissions to {} role(s)", updated);
            }
        } catch (Exception e) {
            log.warn("Permission migration skipped: {}", e.getMessage());
        }

        try {
            int deletedResults = jdbcTemplate.update(
                "DELETE ir FROM inventory_result ir INNER JOIN inventory_record r ON ir.record_id = r.id " +
                "INNER JOIN (SELECT id FROM inventory_task ORDER BY id LIMIT 4) t ON r.task_id = t.id");
            int deletedRecords = jdbcTemplate.update(
                "DELETE r FROM inventory_record r INNER JOIN " +
                "(SELECT id FROM inventory_task ORDER BY id LIMIT 4) t ON r.task_id = t.id");
            int deletedTasks = jdbcTemplate.update("DELETE FROM inventory_task ORDER BY id LIMIT 4");
            if (deletedTasks > 0) {
                log.info("Cleaned up first {} inventory tasks ({} records, {} results)", deletedTasks, deletedRecords, deletedResults);
            }
        } catch (Exception e) {
            log.warn("Inventory cleanup skipped: {}", e.getMessage());
        }
    }
}
