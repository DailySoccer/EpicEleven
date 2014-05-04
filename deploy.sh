set -e
git commit -am "We always commit before a new deploy"
git checkout -b deploy
rm public
cd ../webclient
./build_rsync.sh
cd ../backend
git add .
git commit -am "Including build in deploy branch"
git push heroku deploy
rm -rf public/
git checkout master --force
gir branch -D deploy