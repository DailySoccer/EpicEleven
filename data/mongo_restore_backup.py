#!/usr/bin/env python

import sys
import tempfile
import shutil
import shlex
import subprocess

if len(sys.argv) < 4:
    print 'Usage: {} destination password'.format(sys.argv[0])
    print 'origin and destination must be \'local\', \'production\' or \'staging\''
    print 'password should be quoted'
    sys.exit()

tarfile = sys.argv[1]
destination = sys.argv[2]
password = sys.argv[3]

OPTS_DICT = {'local': '-h localhost:27017 -d dailySoccerDB',
             'production': '-h lamppost.7.mongolayer.com:10078 -d app23671191 -u "admin" -p "{}"'.format(password),
             'staging': '-h lamppost.7.mongolayer.com:10011 -d app26235550 -u "admin" -p "{}"'.format(password)
            }

if destination in OPTS_DICT:
    #Creamos un directorio temporal
    temp_dir = tempfile.mkdtemp()

    UNTAR_CMD = 'tar xzf {} -C {}'.format(tarfile, temp_dir)
    subprocess.call(shlex.split(UNTAR_CMD))

    DUMP_CMD = 'mongodump --dbpath {}'.format(temp_dir)
    subprocess.call(shlex.split(DUMP_CMD))
    dir_created = subprocess.check_output(['ls', temp_dir])[0:-1]

    RESTORE_CMD = 'mongorestore {} {}/{}'.format(OPTS_DICT[destination], temp_dir, dir_created)
    subprocess.call(shlex.split(RESTORE_CMD))

    #Borramos el directorio temporal
    shutil.rmtree(temp_dir)
