#!/bin/sh

which brew &>/dev/null

if [ $? -eq 0 ]
  then
    # If brew is installed, try first brew cask to install brew
    (brew cask install postgres || brew install postgres)

    echo ""
    echo ""
    echo "Abre Postgres.app (Ya está instalado), y te dirá que quiere"
    echo "mover la app a Applications."
    echo ""
    echo "Tras decir que sí, pulsa enter en esta ventana"
    read moved_to_applications
    
    /Applications/Postgres.app/Contents/Versions/9.3/bin/psql -c \
        "CREATE USER postgres WITH PASSWORD 'postgres';" &&
    /Applications/Postgres.app/Contents/Versions/9.3/bin/psql -c \
         "CREATE DATABASE dailysoccerdb WITH ENCODING 'UTF8';" &&
    /Applications/Postgres.app/Contents/Versions/9.3/bin/psql -c \
         "GRANT ALL PRIVILEGES ON DATABASE dailysoccerdb to postgres;" &&
    echo "TODO HA IDO BIEN"

  else
    echo "Necesitas instalar brew: $ ruby -e \"\$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)\""
    echo "Y también puedes usar brew cask: $ brew install caskroom/cask/brew-cask"
    exit 1;
fi
