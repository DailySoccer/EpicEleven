#!/bin/sh
# Run your application using ~run to enable direct compilation on file change.
activator -jvm-debug 9999 "run -Dhttp.port=disabled -Dhttps.port=9000 -Dhttps.keyStore=conf/keystore.jks -Dhttps.keyStorePassword=asdfasdf"
