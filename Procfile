web: target/universal/stage/bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE
worker: target/universal/stage/bin/backend -Dconfig.resource=$CONFIG_FILE -Dconfig.isworker=true

#  -J-javaagent:newrelic/newrelic.jar -J-Dnewrelic.config.file=newrelic/newrelic.yml