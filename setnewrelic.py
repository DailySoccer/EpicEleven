#!/bin/env python
import subprocess

APP = sys.argv[1]
NEW_STATE = sys.argv[2]


def get_setter_cmd(java_opts):
    return 'heroku config:set JAVA_OPTS="{}" --app {}'.format(java_opts, APP)


RELIC_OPTS = ' -J-javaagent:newrelic/newrelic.jar -J-Dnewrelic.config.file=newrelic/newrelic.yml'
CONFIG_GET_CMD = 'heroku config:get JAVA_OPTS --app {}'.format(APP)

heroku_opts = subprocess.call(CONFIG_GET_CMD)

NEW_OPTS = None
if RELIC_OPTS in heroku_opts and NEW_STATE == 'off':
    NEW_OPTS = heroku_opts[0:heroku_opts.find(RELIC_OPTS)]
elif RELIC_OPTS not in heroku_opts and NEW_STATE == 'on':
    NEW_OPTS = heroku_opts + RELIC_OPTS

if NEW_OPTS:
    subprocess.call(get_setter_cmd(NEW_OPTS))
