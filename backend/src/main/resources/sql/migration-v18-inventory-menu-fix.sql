-- 给没有 inventory:view 的角色加上盘点权限
-- 这样所有登录用户都能看到盘点管理菜单
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE p.permission_code IN ('inventory:view', 'inventory:create')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
