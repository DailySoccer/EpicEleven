web: target/universal/stage/bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE -Dpidfile.path="target/universal/stage/WEB_PID"
worker: target/universal/stage/bin/backend -Dconfig.resource=$CONFIG_FILE -Dconfig.isworker=true -Dpidfile.path="target/universal/stage/WORKER_PID"

#  -J-javaagent:newrelic/newrelic.jar -J-Dnewrelic.config.file=newrelic/newrelic.yml