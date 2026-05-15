@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "LINK=%STARTUP%\RemoteTrackpad.lnk"
set "VBS=%~dp0run_hidden.vbs"

powershell -NoProfile -Command ^
  "$s = New-Object -ComObject WScript.Shell; ^
   $l = $s.CreateShortcut('%LINK%'); ^
   $l.TargetPath = 'wscript.exe'; ^
   $l.Arguments = '\"\"%VBS%\"\"'; ^
   $l.WorkingDirectory = '%~dp0'; ^
   $l.Description = 'Remote Trackpad (фон)'; ^
   $l.Save()"

echo.
echo Готово: сервер будет запускаться при входе в Windows (в фоне).
echo Сейчас можно запустить вручную: run_hidden.vbs
echo.
pause
