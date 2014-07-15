#!/bin/sh

if ! which psql >/dev/null
    then
        PATH=$PATH:/Applications/Postgres.app/Contents/Versions/9.3/bin
fi

if ! which heroku >/dev/null
  then
    # If heroku is not installed, we'll have to install it
    which brew &>/dev/null

    if ! which brew >/dev/null
      then
        # If brew is installed, try first brew cask to install brew
        (brew cask install heroku-toolbelt || brew install heroku-toolbelt)
        heroku auth:login

      else
        echo "Necesitas instalar brew: $ ruby -e \"\$(curl -fsSL https://raw.github.com/Homebrew/homebrew/go/install)\""
        echo "Y también puedes usar brew cask: $ brew install caskroom/cask/brew-cask"
        exit 1;
    fi
fi

if which heroku >/dev/null
  then
    if [[ "$1" == "staging" ]]
        then
            #DB="JADE"
            DB="BROWN"
            app="dailysoccer-staging"
    elif [[ "$1" == "production" ]]
        then
            DB="ROSE"
            app="dailysoccer"
    fi
    PGUSER=postgres PGPASSWORD=postgres heroku pg:push dailysoccerdb HEROKU_POSTGRESQL_"$DB" --app "$app"
fi
