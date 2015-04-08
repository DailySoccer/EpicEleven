#!/bin/sh

activator compile stage
boot2docker init
boot2docker start
eval "$(boot2docker shellinit)"

if (grep "epicdocker" /etc/hosts); then
    sudo sed -i .bak "s/.*epicdocker.com/$(boot2docker ip)  epicdocker.com/" /etc/hosts;
else
    echo "$(boot2docker ip)   epicdocker.com" | sudo tee -a /etc/hosts

docker-compose up
