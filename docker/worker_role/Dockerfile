FROM ingensi/oracle-jdk
MAINTAINER Federico Mon <gnu.fede@gmail.com>

ENV CONFIG_FILE docker.conf

RUN mkdir /app
WORKDIR /app

CMD java -Dconfig.resource=$CONFIG_FILE -Dconfig.instanceRole=WORKER_ROLE -cp "target/universal/stage/lib/*" actors.Actors .
