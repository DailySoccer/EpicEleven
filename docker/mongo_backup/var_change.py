#!/usr/bin/env python

import os
import fileinput

# read docker environment variables and set them in the appropriate crontab fragment

for line in fileinput.input("/etc/cron.d/mongo-cron",inplace=1):
    print line.replace("DBADDRXXX", os.environ["DB_PORT_27017_TCP_ADDR"])
