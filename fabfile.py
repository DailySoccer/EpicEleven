#!/usr/bin/env python
# -*- coding: utf-8 -*-

try:
    from fabric.api import local, lcd, env, task
    from fabric.utils import indent, abort
    from fabric.colors import green, red, blue, cyan
except ImportError as e:
    print 'Instalación: '
    print '\t$ sudo easy_install install pip'
    print '\t$ sudo pip uninstall fabric paramiko'
    print '\t$ sudo pip install paramiko==1.10'
    print '\t$ sudo pip install fabric'


from tempfile import mkstemp, mkdtemp
from os import remove
from shutil import move, rmtree
import signal
import sys


remotes_allowed_message = 'The only allowed Heroku remotes are: staging/production'
remotes_allowed = ('staging', 'production')
branches_allowed_to_prod = ('develop', 'master')
production_dests = ('production',)
heroku_apps_names = {'production': 'dailysoccer',
                     'staging':    'dailysoccer-staging'}

@task
def inc_version():
    """
    Increments version in build.sbt file and commit it
    """
    print(blue('Incrementing version...'))
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
    env.incd_version = commit('Incrementando versión para deploy')

def prepare_branch():
    print(blue('Preparing branch...'))

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
        env.warn_only = True
        save_last_commit()
        #inc_version()
        if env.dest in production_dests and env.back_branch_name != 'master':
            env.all_set = merge_branch_to_from('master', env.back_branch_name)

@task
def copydb(origin='', destination='', password=''):
    """
    (mongocp) needs origin, destination and password
    """
    if not origin or not destination:
        abort("""Usage:
                 $ fab copydb:origin=origin,destination=destination,password=password

                 origin must be a production backup file, \'local\', \'production\' or \'staging\'
                 destination must be \'local\', \'production\' or \'staging\'
                 password for destination database should be quoted""")

    if origin == destination:
        abort('Destination cannot be the same as origin')

    OPTS_DICT = {'local':
                     '-h localhost:27017 -d dailySoccerDB',
                 'production':
                     '-h lamppost.7.mongolayer.com:10078 -d app23671191 -u "admin" -p "%s"' % (password),
                 'staging':
                     '-h lamppost.7.mongolayer.com:10011 -d app26235550 -u "admin" -p "%s"' % (password)
                }

    # Name of the MongoDB database in production
    env.prod_app_name = None if origin in OPTS_DICT else 'app23671191'

    if destination in OPTS_DICT:
        #Creamos un directorio temporal
        temp_dir = mkdtemp()

        if env.prod_app_name:
            local('tar xzf %s -C %s' % (tarfile, temp_dir))
            local('mongodump --dbpath %s' % (temp_dir))
            local('mongorestore --drop %s dump/%s' % (OPTS_DICT[destination], origin))
            rmtree('dump')
        else:
            local('mongodump %s -o %s' % (OPTS_DICT[origin], temp_dir))
            dir_created = local('ls %s' % temp_dir, capture=True)
            local('mongorestore --drop %s %s/%s' % (OPTS_DICT[destination], temp_dir, dir_created))

        #Borramos el directorio temporal
        rmtree(temp_dir)


def get_branch_name():
    return local('git symbolic-ref -q HEAD', capture=True)[11:]

def stash():
    print(blue('Stashing if needed...'))
    return 'No local changes to save' not in local('git stash', capture=True)

def unstash():
    print(blue('Unstashing...'))
    local('git stash pop')

def merge_branch_to_from(dest, orig):
    print(blue('Merging %s to %s...' % (orig, dest)))
    if git_checkout(dest):
        local('git pull')
        merge = local('git merge -X theirs %s --commit -m "Merge branch \'%s\'" --no-ff' %
              (orig, orig))
        return merge.succeeded and local('git push').succeeded
    return False

def create_deploy_branch():
    print(blue('Creating deploy branch...'))
    env.switched_to_deploy = local('git checkout -B deploy').succeeded

def remove_admin_folder():
    if env.dest in production_dests:
        print(blue('Removing admin folder...'))
        local('rm -rf admin')

def rm_public():
    print(blue('Removing public folder...'))
    env.public_deleted = local('rm public').succeeded

def commit(message):
    local('git commit -am "%s"' % message)

def prepare_client():
    print blue("Preparing client...")
    env.client_stashed = stash()
    env.client_branch_name = get_branch_name()
    if env.dest in production_dests:
        return merge_branch_to_from('master','develop')
    return True

def build_client():
    print blue("Building client...")
    env.client_built = True
    #client_build = local('./build_for_deploy.sh %s' % env.mode, capture=True)
    #env.client_built = not any(x in client_build.stderr for x in ("error", "failed"))
    """
    if env.dest in production_dests:
        local('./build_for_deploy.sh %s' % env.mode)
    else:
        local('./build.sh %s' % env.mode)
    """

def post_build_client():
    print blue("Client post build...")
    local('git checkout -- .')
    if env.client_stashed:
        unstash()

def commit_for_deploy():
    print blue("Commit for deploy...")
    local('git add .')
    # Allow empty is passed not to crash if no changes are done in the commit
    local('git commit --allow-empty -am "Including build in deploy branch"')

def save_last_commit():
    env.last_commit = local('git rev-parse HEAD', capture=True)

def heroku_push():
    print blue("Pushing to Heroku...")
    #local('git push %s deploy:master --force' % env.dest)

def heroku_version():
    print blue("Getting Heroku version of the app...")
    env.heroku_version = local("heroku releases --app %s | head -2 | tail -1 | awk '{print $1}'" % heroku_apps_names[env.dest], capture=True)
    heroku_set_variable('rel', env.heroku_version)
    print red(env.heroku_version)
    local('git tag %s-%s %s' % (env.heroku_version, env.dest, env.last_commit))

def heroku_set_variable(var_name, var_value):
    print blue("Setting Heroku variable %s" % var_name)
    local("heroku config:set %s=%s --app %s" % (var_name, var_value,
                                                heroku_apps_names[env.dest]))

def wake_dest():
    wakeable_dests = {'staging': 'http://dailysoccer-staging.herokuapp.com'}
    if env.dest in wakeable_dests:
        print blue("Waking up servers...")
        local('curl "%s"' % wakeable_dests[env.dest])

def git_checkout(branch_name_or_file):
    print blue("Returning %s..." % branch_name_or_file)
    return local('git checkout %s' % branch_name_or_file).succeeded

@task
def launch_functional_tests(dest='staging'):
    """
    Launches functional tests
    """
    test_hooks = {'staging': 'https://drone.io/hook?id=github.com/DailySoccer/webtest&token=ncTodtcZ2iTgEIxBRuHR'}
    env.dest = dest if not hasattr(env, 'dest') else env.dest
    if env.dest in test_hooks:
        print blue("Launching functional tests...")
        local('curl "%s"' % test_hooks[env.dest])

@task
def deploy(dest='staging', mode='release'):
    """
    Deploy a Heroku. Parámetros: dest=staging/production,mode=release/debug
    """
    env.dest, env.mode = dest, mode
    env.public_deleted = False
    env.client_branch_name = 'develop'

    prepare_branch()
    if env.all_set:
        print blue('Deploying mode %s to %s from %s' % (env.mode, env.dest,
                                                         env.back_branch_name))
        create_deploy_branch()
        remove_admin_folder()

        #if env.dest in production_dests:
        #    rm_public()
        rm_public()

        with lcd('../webclient'):
            if prepare_client():
                build_client()
        if env.client_built:
            commit_for_deploy()
            heroku_push()
            heroku_version()
            wake_dest()
        return_to_previous_state()
        if env.client_built:
            launch_functional_tests()
    if env.back_stashed:
        unstash()


def handle_sigterm(signal, frame):
    return_to_previous_state()
    if env.back_stashed:
        with lcd('../backend'):
            unstash()
    sys.exit(0)

def return_to_previous_state():
    if env.switched_to_deploy:
        with lcd('../backend'):
            if env.public_deleted:
                git_checkout('-- public')
            git_checkout(env.back_branch_name)
    with lcd('../webclient'):
        git_checkout(env.client_branch_name)
        post_build_client()

def fab_help():
    print 'Instalación: '
    print cyan(indent('$ sudo easy_install install pip'))
    print cyan(indent('$ sudo pip uninstall fabric paramiko'))
    print cyan(indent('$ sudo pip install paramiko==1.10'))
    print cyan(indent('$ sudo pip install fabric'))
    print ''
    print 'Uso:'
    print cyan(indent('$ fab deploy:dest=staging,mode=debug'))
    print ' o bien:'
    print cyan(indent('$ fab deploy:staging,debug'))
    print ''
    print 'Destinos posibles: '+cyan('staging, production')
    print 'Modos posibles: '+cyan('debug, release')
    print ''
    print 'Uso de MongoCP:'
    print cyan(indent('$ fab copydb:origin=production,destination=local[,password="password"]'))
    print ''
    print 'Orígenes posibles: '+cyan('\'/home/.../backup.tar.gz\', \'local\', \'production\' o \'staging\'')
    print 'Destinos posibles: '+cyan('\'local\', \'production\' o \'staging\'')
    print 'Hint: Si la contraseña no funciona quizá puedes entrecomillarla o no ponerla y que la pida'
    print ''
    print 'Para ver la lista completa de comandos:'
    print cyan(indent('$ fab -l'))

signal.signal(signal.SIGTERM, handle_sigterm)
signal.signal(signal.SIGINT, handle_sigterm)

if __name__ == '__main__':
    fab_help()

