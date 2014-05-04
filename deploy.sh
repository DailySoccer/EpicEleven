git commit -am "Before deploy"
git checkout -b deploy
rm public
cd ../webclient
./build_rsync.sh
cd ../backend
git commit -am "Including build in deploy branch"
git push heroku deploy
git checkout master --force
gir branch -D deploy