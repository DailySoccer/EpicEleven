#!/bin/sh
name=$1.tar.gz
mypwd=$PWD
rand=/tmp/snapshot-$1-$RANDOM

if [[ ! -f $name && ! -d $name && ! -L $name ]];
    then
        mkdir $rand && mongodump -d snapshot -o $rand && cd $rand && tar czf $mypwd/$name snapshot
        cd $mypwd && rm -rf $rand
    else 
        echo "el fichero $name existe, borralo primero"
fi
