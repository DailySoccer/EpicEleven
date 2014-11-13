#!/usr/bin/env python2
# -*- coding: utf-8 -*-

try:
    from fabric.api import local, lcd, env, task
    from fabric.colors import green, red, blue
except ImportError, e:
    print 'Instalación: '
    print '\t$ sudo easy_install install pip'
    print '\t$ sudo pip install fabric'

from tempfile import mkstemp
from os import remove
from shutil import move

remotes_allowed_message = 'The only allowed Heroku remotes are: staging/production'
remotes_allowed = ('staging', 'production')
branches_allowed_to_prod = ('develop', 'master')
production_dests = ('production',)

def inc_version():
    LISTA = [('.',2),('.',2),('"',0),('"',2)]
    BUILDFILE = 'build.sbt'
    def general(text, params):
        if not params:
            try:
                return str(int(text)+1)
            except ValueError:
                return '0'
        else:
            our_params = params.pop()
            partitioned = list(text.partition(our_params[0]))
            partitioned[our_params[1]] = general(partitioned[our_params[1]], params)
            return ''.join(partitioned)
    fh, abs_path = mkstemp()
    with open(BUILDFILE, 'r') as f:
        with open(abs_path, 'w') as e:
            for line in f.readlines():
                if line.startswith('version'):
                    line = general(line, LISTA)
                e.write(line)
    remove(BUILDFILE)
    move(abs_path, BUILDFILE)

def prepare_branch():

    env.all_set, env.back_stashed = True, False

    def validate_remote():
        if env.dest not in remotes_allowed:
            print red(remotes_allowed_message)
            env.all_set = False

    def validate_branch():
        if env.back_branch_name == 'deploy':
            print red('Estado inválido para deploy')
            env.all_set = False
        elif env.dest in production_dests and \
                (env.back_branch_name not in branches_allowed_to_prod):
            print red('Destino inválido desde esta rama')
            env.all_set = False

    env.back_branch_name = get_branch_name()
    validate_remote()
    validate_branch()
    if env.all_set:
        env.back_stashed = stash()
        inc_version()
        commit('Incrementando versión para deploy')
        if dest in production_dests and branch_name != 'master':
            env.all_set = merge_branch_to_from(dest, branch_name)


def get_branch_name():
    return local('git symbolic-ref -q HEAD', capture=True)[11:]

def stash():
    return 'No local changes to save' not in local('git stash', capture=True)

def unstash():
    local('git stash pop')

def merge_branch_to_from(dest, orig):
    if git_checkout(dest):
        local('git pull')
        merge = local('git merge -X theirs %s --commit -m "Merge branch \'%s\'" --no-ff' %
              (orig, orig))
        return merge.succeeded and local('git push').succeeded
    return False

def create_deploy_branch():
    local('git checkout -B deploy')

def remove_admin_folder():
    if env.dest in production_dests:
        local('rm -rf admin')

def rm_public():
    env.public_deleted = local('rm public').succeeded

def commit(message):
    local('git commit -am "%s"' % message)

def prepare_client(dest):
    env.client_stashed = stash()
    env.client_branch_name = get_branch_name()
    if env.dest in production_dests:
        return merge_branch_to_from('master','develop')
    return False

def build_client(mode, client_stashed):
    local('./build.sh %s' % mode)

def post_build_client():
    if env.client_stashed:
        unstash()

def commit_for_deploy():
    local('git add .')
    local('git commit -am "Including build in deploy branch"')

def heroku_push():
    local('git push %s deploy:master --force' % env.dest)

def wake_dest():
    wakeable_dests = {'staging': 'http://dailysoccer-staging.herokuapp.com'}
    if env.dest in wakeable_dests:
        local('curl "%s" > /dev/null 2>&1' % wakeable_dests[env.dest])

def git_checkout(branch_name_or_file):
    return local('git checkout %s' % branch_name_or_file).succeeded

def launch_functional_tests():
    test_hooks = {'staging': 'https://drone.io/hook?id=github.com/DailySoccer/webtest&token=ncTodtcZ2iTgEIxBRuHR'}
    if env.dest in test_hooks:
        local('curl "%s" > /dev/null 2>&1 &' % test_hooks[env.dest])

@task
def deploy(dest='staging', mode='release'):
    """
    Deploy a Heroku. Parámetros: dest=staging/production,mode=release/debug
    """
    env.dest, env.mode = dest, mode

    prepare_branch()
    env.client_branch_name = 'develop'
    if all_set:
        print green('Deploying to %s from %s' % (env.dest, env.back_branch_name))
        create_deploy_branch()
        remove_admin_folder()
        rm_public()
        with lcd('../webclient'):
            if prepare_client():
                build_client()
        commit_for_deploy()
        heroku_push()
        wake_dest()
        git_checkout(env.back_branch_name)
        with lcd('../webclient'):
            git_checkout(env.client_branch_name)
            post_build_client()
        if env.public_deleted:
            git_checkout("public")
        launch_functional_tests(dest)
    if env.back_stashed:
        unstash()


def help():
    print 'Uso:'
    print cyan('\n$ fab deploy:dest=staging,mode=debug')
    print ' o bien:'
    print cyan('\n$ fab deploy:staging,debug')
    print ''
    print 'Destinos posibles: staging, production'
    print 'Modos posibles: debug, release'
    print ''
    print 'Para ver la lista de comandos:'
    print cyan('\n$ fab -l')

if __name__ == '__main__':
    help()
