### Paso 1: Instalar Docker Compose
```
  $ brew install docker-compose
```
### Paso 2 (opcional): Elegimos dominio (por defecto: epicdocker.com)
```
  $ vim launch_docker.sh
```
### Paso 3: Lanzamos

Este paso ha quedado algo obsoleto

```
  $ ./launch_docker.sh
```
### Paso 4: Verlo correr
```
  $ open http://epicdocker.com
```

### Notas:
  
  - Docker compose hay que lanzarlo desde el directorio backend (donde está el
    docker-compose.yml)

  - El docker de rabbit se queda tonto, y cuando lo quiere recrear, al
    levantarlo, se muere. Para solucionarlo, hacer docker-compose stop rabbitmq
    y seguidamente docker-compose rm rabbitmq

  - Cada vez que se levanta el sistema, Mongo y Postgres están vacíos

  - Aunque en el esquema de compose están el contenedor web y el worker,
    actualmente he dejado al web con la configuración de staging. Para
    modificarlo, habría que editar 'conf/docker.conf'

  - El planteamiento mío al principio era construir algo para sustituir 
    Heroku. De ahí que la arquitectura y los contenedores a levantar en 
    un principio eran: Web, Worker, Nginx, Postgres y Mongo.
    Posteriormente metí también unos contenedores que periódicamente 
    hacen backups de Mongo y de Postgres, y también un interfaz web para 
    hacer queries al mongo, y otro para apagar y encender los contenedores.
    Después me planteé el sistema de deploy, y decidí que lo más eficiente
    sería un contenedor que con una clave ssh pueda conectarse a github,
    bajarse el cliente y el backend, y preparar todo y dejárselo disponible al
    contenedor web a través de un contenedor de datos. El último paso ha sido
    poner el contenedor que lanza los tests, que depende de que haya un
    selenium en el sistema. Estuve preparando un selenium en un vagrant con
    windows, pero da problemas, y un selenium en contenedor va perfecto.

  - Los contenedores que se bajan el código de nuestros proyectos necesitan
    poder acceder a github, para lo que necesitan unas claves ssh válidas.
    En un principio me pareció que lo ideal es que el contenedor no tenga esas
    claves ssh, y que le sean compartidas. Por eso el contenedor compilador
    funciona así, y se le comparten las claves en tiempo de ejecución. La
    contrapartida de esto es que al no tener clonado ya el proyecto, el tiempo
    gordo de instalar las dependencias del proyecto se va al tiempo de
    ejecución, y cada vez que se compila se va mucho tiempo. Por eso en el
    tester he ido por el otro lado, y las claves son accedidas en tiempo de
    build del contenedor, y se hace un activator compile en la fase de
    construcción del contenedor. El resultado es una imagen más pesada, pero
    que al lanzar las pruebas tiene poco tiempo de overhead.

  - Se necesita tener una clave ssh  '~/.ssh/id_rsa' e '~/.ssh/id_rsa.pub' 
    para el contenedor de compilacion (docker-compose up compiler)

  - Hay que copiar esas claves ssh a docker/tester/ para el contenedor de
    pruebas en la fase de build (docker-compose build tester)

  - El contenedor appdata es un contenedor de datos que mantiene el proyecto
    compilado.

### TODO's:

  - El container de rabbit se queda sucio al apagar, y hay que hacer
    docker-compose rm rabbitmq

  - Sería interesante poner sendos contenedores de datos asociados al
    contenedor de Postgres y el de Mongo, para dar persistencia a las bases de
    datos.

  - Podría ser interesante mover las claves ssh en el compilador a la fase de
    build y acelerar la ejecución de la compilación.

