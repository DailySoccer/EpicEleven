#!/bin/sh

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
    heroku pgbackups:capture --expire
    curl -o latest.dump `heroku pgbackups:url`
    PGPASSWORD=postgres /Applications/Postgres.app/Contents/Versions/9.3/bin/pg_restore \
        --verbose --clean --no-acl --no-owner -h localhost -U postgres -d dailysoccerdb latest.dump
fi
