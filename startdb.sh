#!/bin/bash
mongod run --config /usr/local/etc/mongod.conf &
PGDATA="$HOME/Library/Application Support/Postgres/var-9.3"
export PGDATA

if [[ ! -d $PGDATA ]];
then
    mkdir $PGDATA
fi

command -v postgres >/dev/null 2>&1 || export PATH=$PATH:"/Applications/Postgres.app/Contents/Versions/9.3/bin"
postgres &
