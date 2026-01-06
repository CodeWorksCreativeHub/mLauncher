@echo off
REM ----------------------------
REM Gradle Build Script with Signing
REM Usage: build.bat Prod
REM ----------------------------

REM ----------------------------
REM 1️⃣ Check build type argument
REM ----------------------------
IF "%1"=="" (
    echo No build type specified. Using default: Prod
    SET BUILD_TYPE=Prod
) ELSE (
    SET BUILD_TYPE=%1
)

echo Building %BUILD_TYPE% version...

REM ----------------------------
REM 2️⃣ Check environment variables
REM ----------------------------
IF "%KEY_STORE_PASSWORD%"=="" (
    echo ERROR: KEY_STORE_PASSWORD is not set!
    EXIT /B 1
)
IF "%KEY_ALIAS%"=="" (
    echo ERROR: KEY_ALIAS is not set!
    EXIT /B 1
)
IF "%KEY_PASSWORD%"=="" (
    echo ERROR: KEY_PASSWORD is not set!
    EXIT /B 1
)

echo Using signing key: %KEY_ALIAS%

REM ----------------------------
REM 3️⃣ Build APK and AAB
REM ----------------------------
call ./gradlew clean
call ./gradlew assemble%BUILD_TYPE%Release --no-configuration-cache --refresh-dependencies
call ./gradlew bundle%BUILD_TYPE%Release --no-configuration-cache --refresh-dependencies

REM ----------------------------
REM 4️⃣ Output paths
REM ----------------------------
SET APK_PATH=app\build\outputs\apk\%BUILD_TYPE%\release\app-%BUILD_TYPE%-release.apk
SET BUNDLE_PATH=app\build\outputs\bundle\%BUILD_TYPE%\release\app-%BUILD_TYPE%-release.aab

IF EXIST %APK_PATH% (
    echo APK built successfully: %APK_PATH%
) ELSE (
    echo WARNING: APK not found!
)

IF EXIST %BUNDLE_PATH% (
    echo Bundle built successfully: %BUNDLE_PATH%
) ELSE (
    echo WARNING: Bundle not found!
)

echo Build finished.
pause
