#!/bin/sh
mypwd=$PWD
mongoimport -h xxxx.mongohq.com --port xxxx -u xxxx -pXXXXX --drop -d app26235550 -c templateSoccerPlayersMetadata  --type csv --headerline -f optaPlayerId,name,salary $mypwd/$1
