@echo off
title 财富密码 - Web 服务
cd /d "%~dp0"

echo ============================================
echo   财富密码  -  Web 服务
echo   关闭本窗口即停止服务
echo   端口被占用时会自动顺延到下一个可用端口
echo ============================================

"java" -Dfile.encoding=UTF-8 -jar lottery-web.jar

echo.
echo 服务已停止
pause
