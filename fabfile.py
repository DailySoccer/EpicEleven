#!/usr/bin/env python2

from fabric.api import local

remotes_allowed_message = "The only allowed Heroku remotes are: staging/production"

def prepare_branch():
    branch_name = local("git symbolic-ref -q HEAD", capture=True)[11:]
    return branch_name


def deploy(dest="staging", mode="release"):
    print("Deploying to %s from %s" % (dest, a))



