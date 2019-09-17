echo on 
set JAVA_OPTS=--illegal-access=permit -Xms32m -Xmx128m 
set JAVA_HOME=X:\appz\jdk-12.0.2
set M2_HOME=X:\appz\apache-maven-3.5.0\
set PATH=%M2_HOME%\bin;%JAVA_HOME%\bin;C:\Windows\System32
set MAVEN_OPTS=-Xms32m -Xmx128m 
pushd %~dp0
set script_dir=%CD%
popd


rem mvn -f %script_dir% -DskipTests install || goto :error


REM echo "Running RVN"
REM %JAVA_HOME%\bin\java %JAVA_OPTS% -jar %script_dir%/target/rvn-1.2-SNAPSHOT.jar || goto :error

start /low mvn -Prun

REM pause
REM goto :error

exit

REM pause 
:error

pause
