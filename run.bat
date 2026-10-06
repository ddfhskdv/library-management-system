@echo off
title Library Management System - Spring Boot
echo ========================================================
echo  Starting Library Management System (Spring Boot)
echo ========================================================
echo Using Java from JAVA_HOME or PATH and Maven from PATH...
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Checking Java version:
java -version
if errorlevel 1 (
    echo Java was not found. Install JDK 17 and configure JAVA_HOME or PATH.
    exit /b 1
)
echo.
echo Starting Spring Boot application on http://localhost:8081 ...
mvn spring-boot:run
pause
