web: target/universal/stage/bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE -Dpidfile.path="target/universal/stage/WEB_PID"
worker: java -Dconfig.resource=$CONFIG_FILE -Dconfig.isworker=true -Dpidfile.path="target/universal/stage/WORKER_PID" -cp "target/universal/stage/lib/*" actors.DailySoccerActors .
