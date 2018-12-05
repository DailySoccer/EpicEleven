#!/usr/bin/env python

import os
import fileinput

# read docker environment variables and set them in the appropriate crontab fragment

for line in fileinput.input("/etc/cron.d/postgres-cron",inplace=1):
    print line.replace("PGPASSXXX", os.environ["PGPASSWORD"]).replace("PGUSERXXX", os.environ["POSTGRES_USERNAME"]).replace("DBADDRXXX", os.environ["DB_PORT_5432_TCP_ADDR"])
