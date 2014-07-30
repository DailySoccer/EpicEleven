#!/bin/sh
mypwd=$PWD
mongoimport --drop -d dailySoccerDB -c templateSoccerPlayersMetadata  --type csv --headerline -f optaPlayerId,name,salary $mypwd/$1
