#!/bin/sh

branch_name="$(git symbolic-ref HEAD 2>/dev/null)" ||
branch_name="(unnamed branch)"     # detached HEAD
branch_name=${branch_name##refs/heads/}

destination=$1


remotes_allowed_message="The only allowed Heroku remotes are: staging/production."

# Cambiamos a la rama de deploy, asegurandonos de que esta creada/reseateada

if [[ $(git diff --shortstat ) != "" || $(git diff --shortstat --cached) != "" ]]
    then
        stash="done"
        git stash
    else
        stash=""
fi


if [ $# -eq 0 ]
    then
        if [[ "$branch_name" != "master" && "$branch_name" != "develop" ]]
            then
                #echo "You need to supply the Heroku remote. $remotes_allowed_message"
                echo "If you want to push $branch_name to staging, you must say explicitly:"
                echo "> ./deploy staging"
                exit 1
        elif [[ "$branch_name" == "master" ]]
            then
                destination="production"
        elif [[ "$branch_name" == "develop" ]]
            then 
                destination="staging"
        fi
    else
        if [[ "$1" != "staging" && "$1" != "production" ]]
            then
                echo $remotes_allowed_message
                exit 1
        elif [[ "$1" != "production" && "$branch_name" != "master" ]]
            then
                git checkout master
        fi
fi

git checkout -B deploy

# Tenemos que borrar el symlink y hacer una copia dura de toda la build
rm public
cd ../webclient
./build_rsync.sh
cd ../backend

# Añadimos nuevos archivos (la build) y commitimos
git add .
git commit -am "Including build in deploy branch"

# Push a heroku
git push "$destination" deploy:master --force

#Hacemos una petición a heroku para que vaya levantándose con el código nuevo
curl "http://dailysoccer-staging.herokuapp.com" > /dev/null 2>&1

# Vuelta adonde estabamos
git checkout $branch_name

git branch -D deploy

if [[ "$stash" != "" ]]
    then
        git stash pop
fi

# Restauramos el symlink borrado
git checkout public

# Lanzamos los tests funcionales
curl "https://drone.io/hook?id=github.com/DailySoccer/backend_test&token=sjy4CJrxbBizapoLvUtl" > /dev/null 2>&1 &
