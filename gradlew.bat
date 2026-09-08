@echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java.exe
)
"%JAVA_EXE%" "-Xmx64m" "-Xms64m" "-Dorg.gradle.appname=gradlew" -classpath "" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
endlocal
