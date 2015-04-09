FROM dockerfile/java:oracle-java8
MAINTAINER Federico Mon <gnu.fede@gmail.com>

ENV PORT 9000
ENV CONFIG_FILE docker.conf

EXPOSE 9000
RUN mkdir /app
WORKDIR /app

CMD bin/backend -Dhttp.port=$PORT -Dconfig.resource=$CONFIG_FILE -Dconfig.instanceRole=WEB_ROLE -Dpidfile.path=/dev/null
