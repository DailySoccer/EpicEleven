#!/bin/sh

which brew &>/dev/null

if [ $? -eq 0 ]
  then
    # If brew is installed, try first brew cask to install brew
    #brew install postgresql
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
    echo "Necesitas instalar brew: $ ruby -e \"\$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)\""
    exit 1;
fi
