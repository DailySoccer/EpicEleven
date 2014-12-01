#!/bin/sh

which postgres &>/dev/null

if [ $? -eq 0 ]
  then
    PGDATA="/usr/local/var/postgres"
    export PGDATA
    postgres &
    createdb


    psql -c \
        "CREATE USER postgres WITH PASSWORD 'postgres';" &&
    psql -c \
         "CREATE DATABASE dailysoccerdb WITH ENCODING 'UTF8';" &&
    psql -c \
         "GRANT ALL PRIVILEGES ON DATABASE dailysoccerdb to postgres;" &&
    echo "TODO HA IDO BIEN"

  else
    echo "Necesitas instalar postgres: brew install postgres"
    exit 1;
fi
