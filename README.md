Installing the environment
==========================

- You need to install Java SE JDK 7u51.

    http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html

    After installing, make sure version 7 is installed:

    $ java -version
    java version "1.7.0_51"


- You need to install the Play framework. I recommend using homebrew:

    $ brew install play

- To run the server:

    $ backend> play run

- Or, to debug it, you first enter the play console in debug mode

    $ backend> play debug

    And then, from inside the console, you start the server:

    [backend] $ run

- I recommend installing Intellij Idea 13. To connect the IDE to the debugger, you need to "Edit configuration" and
  add a new remote configuration. Make it point to whatever the port "play debug" tells you.



Deploying to Heroku
===================

- Heroku reference documentation: https://devcenter.heroku.com/categories/heroku-architecture

- After commiting everything to your master branch:

    $ git push heroku master
