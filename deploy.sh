git commit -am "We always commit before a new deploy"
git checkout -b deploy
rm public
cd ../webclient
./build_rsync.sh
cd ../backend
git add .
git commit -am "Including build in deploy branch"
git push heroku deploy:master --force
git checkout master
git branch -D deploy