#!/bin/sh
# El parametro es la carpeta donde hicimos el dump
mongorestore -h localhost:27017 -d dailySoccerDB $1
