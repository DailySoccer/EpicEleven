#!/bin/sh
export JAVA_OPTS="$JAVA_OPTS -javaagent:./newrelic/newrelic.jar"
play run
