@rem ----------------------------------------------------------------------------
@rem ����Venus�Ľű�
@rem
@rem ��Ҫ�������»���������
@rem
@rem    JAVA_HOME           - JDK�İ�װ·��
@rem
@rem ----------------------------------------------------------------------------
@echo off
if "%OS%"=="Windows_NT" setlocal

:CHECK_JAVA_HOME
if not "%JAVA_HOME%"=="" goto SET_VENUS_HOME

echo.
echo ����: �������û���������JAVA_HOME����ָ��JDK�İ�װ·��
echo.
goto END

:SET_VENUS_HOME
set VENUS_HOME=%~dp0..
if not "%VENUS_HOME%"=="" goto START_VENUS

echo.
echo ����: �������û���������VENUS_HOME����ָ��Amoeba�İ�װ·��
echo.
goto END

:START_VENUS

set DEFAULT_OPTS=-server -Xms256m -Xmx256m -Xss128k
set DEFAULT_OPTS=%DEFAULT_OPTS% -XX:+HeapDumpOnOutOfMemoryError -XX:+AggressiveOpts -XX:+UseParallelGC -XX:+UseBiasedLocking -XX:NewSize=64m
set DEFAULT_OPTS=%DEFAULT_OPTS% "-Dproject.home=%VENUS_HOME%"
set DEFAULT_OPTS=%DEFAULT_OPTS% "-Dclassworlds.conf=%VENUS_HOME%\bin\hsb.classworlds"

set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
set CLASSPATH="%VENUS_HOME%\lib\classworlds-1.0.jar"
set MAIN_CLASS="org.codehaus.classworlds.Launcher"

%JAVA_EXE% %DEFAULT_OPTS% -classpath %CLASSPATH% %MAIN_CLASS% %*

:END
if "%OS%"=="Windows_NT" endlocal
pause