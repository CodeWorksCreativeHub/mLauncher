@echo off
REM ----------------------------
REM Gradle Build Script with Arg
REM Usage: build.bat Prod
REM ----------------------------

REM Check if argument is provided
IF "%1"=="" (
    echo No build type specified. Using default: Prod
    SET BUILD_TYPE=Prod
) ELSE (
    SET BUILD_TYPE=%1
)

echo Building %BUILD_TYPE% version...

REM Run Gradle commands
call ./gradlew clean
call ./gradlew assemble%BUILD_TYPE%Release
call ./gradlew assemble%BUILD_TYPE%Bundle

echo Build finished.
pause
