#!/usr/bin/env python
import sys
import shlex
import subprocess

if len(sys.argv) < 3 or sys.argv[1] not in ['dailysoccer', 'dailysoccer-staging']:
    print 'Usage: {} dailysoccer/dailysoccer-staging on/off'.format(sys.argv[0])
    sys.exit()

APP = sys.argv[1]
NEW_STATE = sys.argv[2]


def get_setter_cmd(java_opts):
    """
    Construye el comando de 'set' con las nuevas JAVA_OPTS
    """
    return 'heroku config:set JAVA_OPTS="{}" --app {}'.format(java_opts, APP)


RELIC_OPTS = ' -javaagent:newrelic/newrelic.jar -Dnewrelic.config.file=newrelic/newrelic.yml'
CONFIG_GET_CMD = 'heroku config:get JAVA_OPTS --app {}'.format(APP)

heroku_opts = subprocess.check_output(shlex.split(CONFIG_GET_CMD))[0:-1]

# Ponemos o quitamos las opciones de Relic si cambiamos de estado
NEW_OPTS = heroku_opts + RELIC_OPTS                      if RELIC_OPTS not in heroku_opts and NEW_STATE == 'on' else \
           heroku_opts[0:heroku_opts.find(RELIC_OPTS)]   if RELIC_OPTS in heroku_opts and NEW_STATE == 'off' else \
           None

if NEW_OPTS:
    # Si cambiamos de estado ejecutamos el comando de heroku
    heroku_opts = subprocess.check_output(shlex.split(get_setter_cmd(NEW_OPTS)))[0:-1]

print heroku_opts
