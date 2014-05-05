#!/bin/sh

rm public
cd ../webclient
./build_rsync.sh
cd ../backend
rm -rf public/
ln -s ../webclient/build/web/ public