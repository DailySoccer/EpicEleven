Installing the environment
==========================

- You need to install Java SE JDK 7u51: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html

    Make sure version 7 is installed:
    
    $ java -version

    - java version "1.7.0_51"
    - Java(TM) SE Runtime Environment (build 1.7.0_51-b13)
    - Java HotSpot(TM) 64-Bit Server VM (build 24.51-b03, mixed mode)

- You need to install the Play framework. I recommend using homebrew:

    $ brew install play

- You need to install mongodb
 
    $ brew install mongo

- To run the server:

    $ backend> play run

- Or, to debug it, you first enter the play console in debug mode

    $ backend> play debug

    And then, from inside the console, you start the server:
    
    [backend] $ run

- Install Intellij Idea 13. To connect the IDE to the debugger, you need to "Edit configuration" and
  add a new remote configuration. Make it point to whatever the port "play debug" tells you.



Heroku
===================

- Heroku reference documentation: https://devcenter.heroku.com/categories/heroku-architecture
- Our URL is: http://dailysoccer.herokuapp.com/
