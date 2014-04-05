Installing the environment
==========================

- You need to install the Play framework. I recommend using homebrew:

    $ brew install play

- To run the server:

    $ backend> play run

- Or, to debug it, you first enter the play console in debug mode

    $ backend> play debug

    And then, from inside the console, you start the server:

    [backend] $ run

- I recommend installing Intellij Idea 13, specially for debugging.



Deploying to Heroku
===================

- Heroku reference documentation: https://devcenter.heroku.com/categories/heroku-architecture

- After commiting everything to your master branch:

    $ git push heroku master
