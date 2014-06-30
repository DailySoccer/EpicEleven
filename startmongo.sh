#!/bin/bash
killall -9 mongod && mongod run --config /usr/local/etc/mongod.conf &
