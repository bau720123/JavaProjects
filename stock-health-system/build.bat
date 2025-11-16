@echo off
echo Cleaning jpackage directory...
rmdir /s /q target\jpackage 2>nul
echo Building project...
mvn clean install
echo.
echo Build completed!
pause