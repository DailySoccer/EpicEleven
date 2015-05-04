FROM dockerfile/java:oracle-java8
MAINTAINER Federico Mon <gnu.fede@gmail.com>

RUN apt-get update -y && apt-get install apt-transport-https
#RUN sh -c 'curl https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -'

RUN apt-get install -y git && \
    apt-get clean -y

RUN curl -o activator.zip http://downloads.typesafe.com/typesafe-activator/1.3.2/typesafe-activator-1.3.2.zip
RUN unzip activator.zip -d /opt/

RUN echo 'export PATH="$PATH:/usr/lib/dart/bin:/opt/activator-1.3.2"' | tee -a ~/.profile 

CMD . ~/.profile && \
    eval "$(ssh-agent -s)" && \
    ssh-add ~/.ssh/id_rsa && \
    git clone git@github.com:DailySoccer/webtest.git && \
    cd webtest && \
    git checkout feature/docker && \
    bash ./playTest.sh -B IE_11
