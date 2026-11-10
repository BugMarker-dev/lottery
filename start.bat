@echo off
title 财富密码 - Web 服务
cd /d "%~dp0"

echo ============================================
echo   财富密码  -  Web 服务
echo   关闭本窗口即停止运行
echo   端口被占用时，会自动顺延到下一个可用端口
echo ============================================

rem 优先使用系统 PATH 中的 java；找不到则退回项目自带的 JRE
set "JAVA_EXE=java"
if not exist "packaging\app\jre\bin\java.exe" goto run
if defined LOTTERY_JAVA_ONLY goto run
rem 探测系统 java 是否真的可用（有些机器 PATH 里没有）
where java >nul 2>nul
if errorlevel 1 (
    echo 未检测到系统 Java，改用项目自带的 JRE...
    set "JAVA_EXE=packaging\app\jre\bin\java.exe"
)

:run
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar lottery-web.jar

echo.
echo 服务已停止
pause
