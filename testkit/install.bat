@echo off
rem Installs the SkyCase test-kit datapack into every dev-client singleplayer world.
setlocal
cd /d "%~dp0"
for /d %%W in ("..\versions\26.2\run\saves\*") do (
  if exist "%%W\datapacks\skycase-testkit" rmdir /s /q "%%W\datapacks\skycase-testkit"
  xcopy /e /i /q skycase-testkit "%%W\datapacks\skycase-testkit" >nul
  echo installed into %%~nxW
)
echo In-game: /reload  then  /function skycase:help
endlocal
