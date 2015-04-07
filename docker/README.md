### Paso 1: Compilamos la build:
```
  $ activator compile stage
```
### Paso 2: Instalamos docker (caso mac: https://docs.docker.com/installation/mac/)

### Paso 3 (caso mac): Lanzamos boot2docker
```
  $ boot2docker init
  $ boot2docker start
  $ eval "$(boot2docker shellinit)"
```
### Paso 4: Construimos nuestros contenedores web y worker
```
  $ cd docker
  $ cd web_role
  $ docker build -t dailysoccer/web:latest .
  $ cd ../worker_role
  $ docker build -t dailysoccer/worker:latest .
  $ cd ../..
```
### Paso 5: Lanzamos contenedores: Mongo, Postgres, Web y Worker
```
  $ docker run --name my-mongo -d mongo:2.6
  $ docker run --name some-postgres -e POSTGRES_PASSWORD=postgres -d postgres
  $ docker run -d -p 80:80 -v /var/run/docker.sock:/tmp/docker.sock -t jwilder/nginx-proxy
  $ docker run -d -v $PWD:/app:rw -e VIRTUAL_HOST=foo.bar.com -P --link some-postgres:postgres --link my-mongo:mongo dailysoccer/web
  $ docker run -d -v $PWD:/app:rw -e VIRTUAL_HOST=foo.bar.com -P --link some-postgres:postgres --link my-mongo:mongo dailysoccer/web
  $ docker run -d -v $PWD:/app:rw --link some-postgres:postgres --link my-mongo:mongo dailysoccer/worker
```
### Paso 6: Verlo correr
```
  $ $ open "http://$(boot2docker ip)"
```
