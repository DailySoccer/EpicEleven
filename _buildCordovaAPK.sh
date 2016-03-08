#!/bin/bash

cd ../webclient
./build.sh

echo "Copying public folder to cordova 'www' folder"
cd ../backend
cp -Rf ./public/ ./epiceleven/www/

cd ./epiceleven
sudo cordova build android

cd ..
open epiceleven/platforms/android/build/outputs/apk/
