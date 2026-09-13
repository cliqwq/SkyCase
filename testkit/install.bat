@echo off
rem Installs the SkyGrab test-kit datapack into every dev-client singleplayer world.
setlocal
cd /d "%~dp0"
for /d %%W in ("..\versions\26.2\run\saves\*") do (
  if exist "%%W\datapacks\skygrab-testkit" rmdir /s /q "%%W\datapacks\skygrab-testkit"
  xcopy /e /i /q skygrab-testkit "%%W\datapacks\skygrab-testkit" >nul
  echo installed into %%~nxW
)
echo In-game: /reload  then  /function skygrab:help
endlocal
