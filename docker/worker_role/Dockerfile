FROM dockerfile/java:oracle-java8
MAINTAINER Federico Mon <gnu.fede@gmail.com>

ENV CONFIG_FILE docker-worker.conf

VOLUME /app

WORKDIR /app

CMD java -Dconfig.resource=$CONFIG_FILE -Dconfig.instanceRole=WORKER_ROLE -cp "lib/*" actors.Actors .
