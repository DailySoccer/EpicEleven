#!/bin/bash

cd ../webclient
./build.sh

echo "Copying public folder to cordova 'www' folder"
cd ../backend
cp -Rf ./public/ ./epiceleven/www/

cd ./epiceleven
sudo cordova build ios

cd ..
open epiceleven/platforms/ios/
