#!/bin/bash
mongod run --config /usr/local/etc/mongod.conf &
PGDATA="/usr/local/var/postgres"
export PGDATA

if [[ ! -d $PGDATA ]];
then
    mkdir $PGDATA
fi

postgres &
