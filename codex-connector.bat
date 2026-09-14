@echo off
setlocal

set "MASON_ROOT=%~dp0"
set "CONNECTOR_HOME=%MASON_ROOT%codex-connector\build\install\codex-connector"
pushd "%MASON_ROOT%" >NUL

if not exist "%CONNECTOR_HOME%\bin\codex-connector.bat" (
    echo Building the Windows Connector package...
    set "JAVA_EXE=java.exe"
    if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    if not exist "%MASON_ROOT%gradle\wrapper\gradle-wrapper.jar" (
        echo Gradle wrapper JAR is missing.
        exit /b 1
    )
    "%JAVA_EXE%" -classpath "%MASON_ROOT%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :codex-connector:installDist
    if errorlevel 1 (
        echo Connector build failed.
        popd
        exit /b 1
    )
)

call "%CONNECTOR_HOME%\bin\codex-connector.bat" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
