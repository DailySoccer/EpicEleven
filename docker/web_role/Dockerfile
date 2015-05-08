FROM library/java:8-jdk
MAINTAINER Federico Mon <gnu.fede@gmail.com>

ENV PORT 9000
ENV CONFIG_FILE docker.conf

VOLUME /app

EXPOSE 9000
WORKDIR /app

CMD bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE -Dconfig.instanceRole=WEB_ROLE -Dpidfile.path=/dev/null
