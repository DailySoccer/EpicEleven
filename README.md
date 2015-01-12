Installing the environment
==========================

- You need to install Java SE JDK 7u67: http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html

    Make sure version 7 is installed:
    
    $ java -version

    - java version "1.7.0_67"
    - Java(TM) SE Runtime Environment (build 1.7.0_67-b01)
    - Java HotSpot(TM) 64-Bit Server VM (build 24.65-b04, mixed mode)

- You need to install the Play framework. Use homebrew:

    $ brew install typesafe-activator

- You need to install the databases:
 
    $ brew install mongo
    $ brew install postgres

- To run the server with debugging enabled:

    $ backend> ./_debug.sh
    
- UPDATE: To enable HTTPS:
  - Edit the file "/etc/hosts" as root, for example:
  
    $ sudo vim /etc/hosts

    Add " backend.epiceleven.localhost" after "127.0.0.1 localhost"

  - Add the ssl.crt certificate to the Keychain Access:
  
    $ open conf/ssl.crt
    
    - Click on "Don't trust"
    - Select the certificate on the list and click in the "i" icon in the toolbar
    - In the "Trust" section, at "When using this certificate" choose: "Always Trust"
    - Close the window, it will ask for your password. Afterwards you can close the application.

  - Route is now: https://backend.epiceleven.localhost:9000

- Install Intellij Idea 13 and then follow this instructions:

    http://www.playframework.com/documentation/2.3.x/IDE

  To connect the IDE to the debugger, you need to "Edit configuration" and add a new remote configuration. Make it point
  to whatever the port "play debug" tells you (9999 in my case)



Heroku
===================

- Heroku reference documentation: https://devcenter.heroku.com/categories/heroku-architecture

- Our URLs are: 
    
    + http://dailysoccer.herokuapp.com/
    + http://dailysoccer-staging.herokuapp.com/
    
- The remote repository has to be configured with the proper names (production/staging), like this:

    + $ backend> git remote add production git@heroku.com:dailysoccer.git
    + $ backend> git remote add staging git@heroku.com:dailysoccer-staging.git

- The deployment script accepts a parameter to specify the target heroku app:
    $ backend> ./deploy.sh staging | production    

- There has to be a variable set in the deployment enviroment to specify the config file. This variable is called $CONFIG_FILE and
  is referenced from the Procfile. To set this variable from the command line, use:

  + heroku config:add --app dailysoccer-staging CONFIG_FILE=staging.conf
  + heroku config:add --app dailysoccer CONFIG_FILE=production.conf
  
- If you ever need to force a rebuild, for instance when upgrading Play, have a look at the "Clean builds" section of:

    https://devcenter.heroku.com/articles/scala-support
