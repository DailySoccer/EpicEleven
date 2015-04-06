FROM ingensi/oracle-jdk
MAINTAINER Federico Mon <gnu.fede@gmail.com>

ENV PORT 9000
ENV CONFIG_FILE docker.conf

EXPOSE 9000
RUN mkdir /app
WORKDIR /app

CMD target/universal/stage/bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE -Dconfig.instanceRole=WEB_ROLE
