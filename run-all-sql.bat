@echo off
chcp 65001 >nul
echo ============================================
echo   固定资产管理系统 - 数据库初始化脚本
echo ============================================
echo.

set SQL_DIR=backend\src\main\resources\sql
set MYSQL_USER=root
set MYSQL_PASS=123456
set MYSQL_HOST=localhost
set MYSQL_PORT=3306

echo 数据库连接: %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%
echo 密码: %MYSQL_PASS%
echo.
echo 如需修改密码，请编辑此文件中的 MYSQL_PASS 变量
echo.

set /p CONFIRM="按 Enter 开始执行，或按 Ctrl+C 取消..."

echo.
echo ============================================
echo [1/10] 执行 init.sql (建库建表 + 基础数据)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\init.sql"
if errorlevel 1 (
    echo [错误] init.sql 执行失败，请检查密码和数据库连接
    pause
    exit /b 1
)
echo [完成] init.sql

echo.
echo ============================================
echo [2/10] 执行 migration-v2-lifecycle.sql (生命周期单据表)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v2-lifecycle.sql"
if errorlevel 1 (
    echo [错误] migration-v2-lifecycle.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v2-lifecycle.sql

echo.
echo ============================================
echo [3/10] 执行 migration-v3-approval.sql (审批流表)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v3-approval.sql"
if errorlevel 1 (
    echo [错误] migration-v3-approval.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v3-approval.sql

echo.
echo ============================================
echo [4/10] 执行 migration-v4-finance.sql (财务同步表)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v4-finance.sql"
if errorlevel 1 (
    echo [错误] migration-v4-finance.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v4-finance.sql

echo.
echo ============================================
echo [5/10] 执行 migration-v5-rbac.sql (RBAC权限表)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v5-rbac.sql"
if errorlevel 1 (
    echo [错误] migration-v5-rbac.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v5-rbac.sql

echo.
echo ============================================
echo [6/10] 执行 migration-v6-finance-enhance.sql (财务表增强)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v6-finance-enhance.sql"
if errorlevel 1 (
    echo [错误] migration-v6-finance-enhance.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v6-finance-enhance.sql

echo.
echo ============================================
echo [7/10] 执行 migration-v13-demo-data.sql (演示数据)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v13-demo-data.sql"
if errorlevel 1 (
    echo [错误] migration-v13-demo-data.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v13-demo-data.sql

echo.
echo ============================================
echo [8/10] 执行 migration-v15-1-depreciation-trend-variation.sql (折旧趋势修复)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v15-1-depreciation-trend-variation.sql"
if errorlevel 1 (
    echo [错误] migration-v15-1-depreciation-trend-variation.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v15-1-depreciation-trend-variation.sql

echo.
echo ============================================
echo [9/10] 执行 migration-v15-master-data-demo-time.sql (基础数据字典)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v15-master-data-demo-time.sql"
if errorlevel 1 (
    echo [错误] migration-v15-master-data-demo-time.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v15-master-data-demo-time.sql

echo.
echo ============================================
echo [10/11] 执行 migration-v16-demo-roles.sql (多角色演示账号)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v16-demo-roles.sql"
if errorlevel 1 (
    echo [错误] migration-v16-demo-roles.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v16-demo-roles.sql

echo.
echo ============================================
echo [11/11] 执行 migration-v17-inventory-result.sql (盘点结果明细表)
echo ============================================
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -h%MYSQL_HOST% -P%MYSQL_PORT% < "%SQL_DIR%\migration-v17-inventory-result.sql"
if errorlevel 1 (
    echo [错误] migration-v17-inventory-result.sql 执行失败
    pause
    exit /b 1
)
echo [完成] migration-v17-inventory-result.sql

echo.
echo ============================================
echo   全部执行完成！
echo   数据库: fixed_asset_lifecycle_system
echo   默认账号: admin / 123456
echo ============================================
pause