#!/bin/sh
mypwd=$PWD
mongoexport -d dailySoccerDB -c templateSoccerPlayersMetadata  --csv -f optaPlayerId,name,salary -o $mypwd/$1
