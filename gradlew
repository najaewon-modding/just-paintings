#!/bin/sh

APP_HOME=$( cd -P "${0%/*}" > /dev/null && printf '%s\n' "$PWD" ) || exit
JAVACMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "" -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
