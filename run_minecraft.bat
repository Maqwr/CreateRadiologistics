@echo off
title Running Create: Radiologistics
echo ===================================================
echo     Launching Minecraft with Create: Radiologistics
echo ===================================================
echo.
echo Running NeoForge Client via Gradle...
echo.
call gradlew.bat runClient
echo.
echo ===================================================
echo     Minecraft client has shut down.
echo ===================================================
pause
