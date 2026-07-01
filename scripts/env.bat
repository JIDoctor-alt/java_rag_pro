@echo off
set JAVA_HOME=D:\work\jdk21
set MAVEN_HOME=D:\work\apache-maven-3.9.9-bin\apache-maven-3.9.9
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo JAVA_HOME=%JAVA_HOME%
echo MAVEN_HOME=%MAVEN_HOME%
java -version
mvn -version
