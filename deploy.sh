#!/bin/sh
set -e

remotes_allowed_message="The only allowed Heroku remotes are: staging/production."

if [[ "$2" != "" ]]
    then
    # Mode debug o release para el webclient
    mode=$2
    destination=$1
elif [[ "$1" == "debug" || "$1" == "release" ]]
    then
    mode=$1
    destination="staging"
elif [[ "$1" == "production" || "$1" == "staging" ]]
    then
    mode="release"
    destination=$1
else
    mode="release"
    destination="staging"
fi

branch_name="$(git symbolic-ref HEAD 2>/dev/null)" ||
branch_name="(unnamed branch)"     # detached HEAD
branch_name=${branch_name##refs/heads/}

if [[ "$branch_name" == "deploy" ]]
    then
        echo "ERROR, estado inválido para deploy"
        exit 1
fi


if [[ $(git diff --shortstat ) != "" || $(git diff --shortstat --cached) != "" ]]
    then
        stash="done"
        git stash
    else
        stash=""
fi


if [ $# -eq 0 ]
    then
        if [[ "$branch_name" == "master" ]]
            then
                destination="production"
        fi
    else
        if [[ "$destination" != "staging" && "$destination" != "production" ]]
            then
                echo $remotes_allowed_message
                exit 1
            fi
        if [[ "$destination" == "production" && "$branch_name" != "master" ]]
            then
                if [[ "$branch_name" == "develop" ]]
                then
                    git checkout master
                    git merge develop
                else
                    echo "No se puede hacer deploy a producción desde esta rama"
                fi

        fi
fi

echo "deploying to: $destination"
echo "mode: $mode"

# Cambiamos a la rama de deploy, asegurandonos de que esta creada/reseateada
git checkout -B deploy

# Tenemos que borrar el symlink y hacer una copia dura de toda la build
rm public
cd ../webclient
if [[ $destination == "production" ]]
then
    git checkout master
    git merge develop
fi
./build_rsync.sh $mode
cd ../backend

# Añadimos nuevos archivos (la build) y commitimos
git add .
git commit -am "Including build in deploy branch"

# Push a heroku
git push "$destination" deploy:master --force

if [[ "$destination" == "staging" ]]
then
    #Hacemos una petición a heroku para que vaya levantándose con el código nuevo
    curl "http://dailysoccer-staging.herokuapp.com" > /dev/null 2>&1
fi

# Vuelta adonde estabamos
git checkout $branch_name

git branch -D deploy

if [[ "$stash" != "" ]]
    then
        git stash pop
fi

# Restauramos el symlink borrado
git checkout public

if [[ "$destination" == "staging" ]]
then
    # Lanzamos los tests funcionales
    curl "https://drone.io/hook?id=github.com/DailySoccer/webtest&token=ncTodtcZ2iTgEIxBRuHR" > /dev/null 2>&1 &
fi
