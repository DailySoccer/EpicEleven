#!/bin/sh

mongorestore -h localhost:27017 -d dailySoccerDB $1
