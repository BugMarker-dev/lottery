Unicode true

!define APPNAME "财富密码"
!define COMPANY "Lottery"
!define APPID "DaLeTouWeb"
; 旧版本（改名前的）快捷方式，安装时顺手清掉
!define OLDAPPNAME "大乐透号码生成器"

Name "${APPNAME}"
OutFile "财富密码Setup.exe"
Icon "app\app.ico"
UninstallIcon "app\app.ico"
InstallDir "$PROGRAMFILES64\DaLeTouWeb"
InstallDirRegKey HKLM "Software\${APPID}" "InstallDir"
RequestExecutionLevel admin

SetCompressor /SOLID lzma

Page directory
Page instfiles

Section "Install"
  SetOutPath $INSTDIR

  File "app\lottery-web.jar"
  File "app\run.bat"
  File "app\app.ico"
  File /r "app\static"
  File /r "app\jre"

  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; 清掉改名前的旧快捷方式，避免桌面出现两个图标
  Delete "$DESKTOP\${OLDAPPNAME}.lnk"
  RMDir /r "$SMPROGRAMS\${OLDAPPNAME}"

  CreateDirectory "$SMPROGRAMS\${APPNAME}"
  CreateShortCut "$SMPROGRAMS\${APPNAME}\${APPNAME}.lnk" "$INSTDIR\run.bat" "" "$INSTDIR\app.ico" 0 SW_SHOWNORMAL "" "${APPNAME} Web 服务"
  CreateShortCut "$SMPROGRAMS\${APPNAME}\卸载.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\app.ico" 0 SW_SHOWNORMAL "" "卸载 ${APPNAME}"
  CreateShortCut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\run.bat" "" "$INSTDIR\app.ico" 0 SW_SHOWNORMAL "" "${APPNAME} Web 服务"

  WriteRegStr HKLM "Software\${APPID}" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}" "DisplayName" "${APPNAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}" "UninstallString" "$INSTDIR\uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}" "DisplayIcon" "$INSTDIR\app.ico"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}" "DisplayVersion" "2.1"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}" "EstimatedSize" 120000
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\${APPNAME}.lnk"
  RMDir /r "$SMPROGRAMS\${APPNAME}"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPID}"
  DeleteRegKey HKLM "Software\${APPID}"
SectionEnd
