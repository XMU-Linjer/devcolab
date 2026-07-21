@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"

echo DevCollab 本地环境将由统一编排脚本启动。
echo 运行日志和进程状态保存在 logs\local-demo\。
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%tools\local-demo.ps1" start -VerifyAfterStart
if errorlevel 1 (
    echo.
    echo 启动失败。请查看 logs\local-demo\ 下对应服务日志。
    pause
    exit /b 1
)

echo.
echo DevCollab 已通过启动验收：http://localhost:8088
pause
