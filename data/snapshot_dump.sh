#!/bin/sh
name=$1.tar.gz
mypwd=$PWD
rand=/tmp/snapshot-$1-$RANDOM

if [[ ! -f $name && ! -d $name && ! -L $name ]];
then
    rm $name
fi
mkdir $rand && mongodump -d snapshot -o $rand && cd $rand && tar czf $mypwd/$name snapshot
cd $mypwd && rm -rf $rand
