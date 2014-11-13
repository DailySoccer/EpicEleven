#!/usr/bin/env python2
# -*- coding: utf-8 -*-

from fabric.api import local

remotes_allowed_message = 'The only allowed Heroku remotes are: staging/production'
remotes_allowed = ('staging', 'production')
branches_allowed_to_prod = ('develop', 'master')
production_dests = ('production',)


def prepare_branch(dest):
    all_set, stashed = True, False
    if dest not in remotes_allowed:
        print remotes_allowed_message
        all_set = False

    branch_name = get_branch_name()

    if branch_name == 'deploy':
        print 'Estado inválido para deploy'
        all_set = False
    elif dest in production_dests and (branch_name not in branches_allowed_to_prod):
        print 'Destino inválido desde esta rama'
        all_set = False

    stashed = stash()

    increment_version()
    commit('Incrementando versión para deploy')

    if all_set:
        if dest in production_dests and branch_name != 'master':
            all_set = merge_branch_to_from(dest, branch_name)

    print('Deploying to %s from %s' % (dest, branch_name))
    return all_set, stashed, branch_name


def get_branch_name():
    return local('git symbolic-ref -q HEAD', capture=True)[11:]


def stash():
    return 'No local changes to save' not in local('git stash', capture=True)


def unstash():
    stash = local('git stash pop')


def merge_branch_to_from(dest, orig):
    local('git checkout %s' % dest)
    local('git pull')
    local('git merge -X theirs %s --commit -m "Merge branch \'%s\'" --no-ff' % (orig, orig))
    local('git push')


def deploy_branch():
    local('git checkout -B deploy')


def remove_admin_folder(dest):
    if dest in production_dests:
        local('rm -rf admin')


def rm_public():
    local('rm public')
    return True


def build_client(mode):
    local('./build_for_deploy.sh %s' % mode)


def increment_version():
    inc_version.main()


def commit(message):
    local('git commit -am "%s"' % message)


def prepare_client(dest):
    client_stashed = stash()
    client_branch_name = get_branch_name()
    if dest in production_dests:
        merge_branch_to_from('master', 'develop')
    build_client(mode)
    if client_stashed:
        unstash()
    return client_branch_name


def commit_for_deploy():
    local('git add .')
    local('git commit -am "Including build in deploy branch"')


def heroku_push(dest):
    local('git push %s deploy:master --force' % dest)


def wake_dest(dest='staging'):
    wakeable_dests = {'staging': 'http://dailysoccer-staging.herokuapp.com'}

    if dest in wakeable_dests.keys:
        local('curl "%s" > /dev/null 2>&1' % wakeable_dests[dest])


def git_checkout(branch_name):
    local('git checkout %s' % branch_name)


def launch_functional_tests(dest):
    test_hooks = {'staging': 'https://drone.io/hook?id=github.com/DailySoccer/webtest&token=ncTodtcZ2iTgEIxBRuHR'}

    if dest in test_hooks.keys:
        local('curl "%s" > /dev/null 2>&1 &' % test_hooks[dest])


def deploy(dest='staging', mode='release'):
    all_set, stashed, branch_name = prepare_branch(dest)
    client_branch_name = 'develop'
    public_deleted = False
    if all_set:
        deploy_branch()
        remove_admin_folder(dest)
        public_deleted = rm_public()
        with cd('../webclient'):
            client_branch_name = prepare_client(dest)
        commit_for_deploy()
        heroku_push(dest)
        wake_dest(dest)
        git_checkout(branch_name)
        with cd('../webclient'):
            git_checkout(client_branch_name)
        if public_deleted:
            git_checkout("public")
        launch_functional_tests(dest)
    if stashed:
        unstash()

