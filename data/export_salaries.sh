#!/bin/sh
mypwd=$PWD
mongoexport -d dailySoccerDB -c templateSoccerPlayers  --csv -f optaPlayerId,name,salary -o $mypwd/$1