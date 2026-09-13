@echo off
rem SkyCase dev launcher: builds + starts the Minecraft 26.2 dev client with the mod loaded.
rem DevAuth (bundled in the dev runtime) opens a Microsoft login on first run so you can join Hypixel.
rem Account/config lives in %USERPROFILE%\.devauth\config.toml after the first login.
setlocal
cd /d "%~dp0"
set JAVA_TOOL_OPTIONS=-Ddevauth.enabled=true -Ddevauth.account=main
call gradlew.bat :26.2:runClient %*
endlocal
