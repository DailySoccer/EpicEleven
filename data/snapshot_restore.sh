#!/bin/sh
name=$1.tar.gz
mypwd=$PWD
rand=/tmp/snapshot-$1-$RANDOM

if [[ -f $name ]];
    then
        mkdir $rand && cd $rand && tar xzf $mypwd/$name && mongorestore --drop -d snapshot snapshot
        cd $mypwd && rm -rf $rand
    else 
        echo "el fichero $name no existe"
fi
