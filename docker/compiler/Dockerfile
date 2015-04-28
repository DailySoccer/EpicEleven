FROM dockerfile/java:oracle-java8
MAINTAINER Federico Mon <gnu.fede@gmail.com>

VOLUME /app

RUN apt-get update -y && apt-get install apt-transport-https
RUN sh -c 'curl https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -'
RUN sh -c 'curl https://storage.googleapis.com/download.dartlang.org/linux/debian/dart_stable.list > /etc/apt/sources.list.d/dart_stable.list'
RUN curl -sL https://deb.nodesource.com/setup | bash - 

RUN apt-get install -y nodejs dart git && \
    apt-get clean -y

RUN curl -o activator.zip http://downloads.typesafe.com/typesafe-activator/1.3.2/typesafe-activator-1.3.2.zip
RUN unzip activator.zip -d /opt/

RUN ln -s /usr/lib/dart /usr/lib/dart/bin/dart-sdk
RUN echo 'export DART_SDK="/usr/lib/dart"' | tee -a ~/.profile
RUN echo 'export PATH="$PATH:/usr/lib/dart/bin:/opt/activator-1.3.2"' | tee -a ~/.profile 

CMD . ~/.profile && \
    eval "$(ssh-agent -s)" && \
    ssh-add ~/.ssh/id_rsa && \
    git clone git@github.com:DailySoccer/backend.git && \
    cd backend && \
    git checkout feature/docker && \
    cd .. &&  \
    git clone git@github.com:DailySoccer/webclient.git && \
    cd webclient && \
    npm install -g grunt-cli && \
    npm install grunt grunt-contrib-less less less-plugin-clean-css && \
    pub get && \
    bash ./build_for_deploy.sh && \
    cd ../backend && \
    activator "set offline := true" clean compile stage && \
    cp -R /data/backend/target/universal/stage/* /app  && \
    mkdir -p /app/common/app/model && \
    cp -R /data/backend/common/app/model/migrations /app/common/app/model/
