#!/bin/sh

VIRTUAL_HOST="epicdocker.com"

activator compile stage
boot2docker init
boot2docker start
eval "$(boot2docker shellinit)"

if (grep $VIRTUAL_HOST /etc/hosts); then
    sudo sed -i .bak "s/.*$VIRTUAL_HOST.*/$(boot2docker ip)  $VIRTUAL_HOST/" /etc/hosts;
else
    echo "$(boot2docker ip)   $VIRTUAL_HOST" | sudo tee -a /etc/hosts;
fi

sed -i .bak "s/.*    VIRTUAL_HOST.*/    VIRTUAL_HOST: $VIRTUAL_HOST/" docker-compose.yml;

docker-compose up
