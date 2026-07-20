@echo off
REM Build the WarfaceGPT RE Plugin for Ghidra
REM Requires: Maven 3.6+, JDK 17+, GHIDRA_INSTALL_DIR set

echo === WarfaceGPT RE Plugin Build ===

if "%GHIDRA_INSTALL_DIR%"=="" (
    echo ERROR: GHIDRA_INSTALL_DIR is not set.
    echo Set it to your Ghidra installation directory:
    echo   set GHIDRA_INSTALL_DIR=C:\ghidra_11.0_PUBLIC
    exit /b 1
)

if not exist "%GHIDRA_INSTALL_DIR%" (
    echo ERROR: GHIDRA_INSTALL_DIR does not exist: %GHIDRA_INSTALL_DIR%
    exit /b 1
)

echo GHIDRA_INSTALL_DIR: %GHIDRA_INSTALL_DIR%
echo Building...

call mvn clean package -Dghidra.install.dir="%GHIDRA_INSTALL_DIR%"

if exist "target\WarfaceGPT-RE-Plugin-1.0.0.zip" (
    echo.
    echo === Build Successful ===
    echo Extension ZIP: target\WarfaceGPT-RE-Plugin-1.0.0.zip
    echo.
    echo To install in Ghidra:
    echo   1. Open Ghidra
    echo   2. File - Install Extensions...
    echo   3. Click '+' and select the ZIP file
    echo   4. Restart Ghidra
    echo   5. Window - WarfaceGPT RE
) else (
    echo ERROR: Build failed. ZIP not found.
    exit /b 1
)