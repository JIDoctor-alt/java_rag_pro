# 本地开发环境变量（按需修改路径）
$env:JAVA_HOME = "D:\work\jdk21"
$env:MAVEN_HOME = "D:\work\apache-maven-3.9.9-bin\apache-maven-3.9.9"
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;" + $env:PATH

Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host "MAVEN_HOME = $env:MAVEN_HOME"
java -version
mvn -version
