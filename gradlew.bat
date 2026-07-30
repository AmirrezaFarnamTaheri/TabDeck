@ECHO OFF
SET APP_HOME=%~dp0
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF NOT EXIST "%CLASSPATH%" (
  ECHO gradle-wrapper.jar is missing. Open the project in Android Studio or run: gradle wrapper --gradle-version 9.1.0 1>&2
  EXIT /B 1
)
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
