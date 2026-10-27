@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "" @ECHO OFF
@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJcmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJcmd
if exist "%JAVACMD%" goto chkMHome

echo The JAVA_HOME environment variable is not defined correctly >&2
echo This environment variable is needed to run this program >&2
echo NB: JAVA_HOME should point to a JDK not a JRE >&2
goto error

:chkMHome
set "DIRNAME=%~dp0"
if "%DIRNAME:~-1%"=="\" set "DIRNAME=%DIRNAME:~0,-1%"
set "APP_HOME=%DIRNAME%"

@REM Find maven-wrapper.jar
set "WRAPPER_JAR=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

@REM Execute Maven Wrapper
"%JAVACMD%" -Dfile.encoding=UTF-8 "-Dmaven.multiModuleProjectDirectory=%APP_HOME%" -cp "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%
