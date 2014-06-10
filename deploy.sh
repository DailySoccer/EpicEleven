#!/bin/sh

# Cambiamos a la rama de deploy, asegurandonos de que esta creada/reseateada
git stash
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
git push heroku deploy:master --force

# Vuelta a la rama principal
git checkout master
git branch -D deploy
git stash apply

# Restauramos el symlink borrado
git checkout public