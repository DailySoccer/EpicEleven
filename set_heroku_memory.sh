# Para que sea facil recordar como cambiar las opciones de memoria de la JVM. 
# $1 es el nombre de la app => dailysoccer, dailysoccer-staging
#
# En una instancia 1x en el primer push crea un JAVA_OPTS con -Xmx384m -Xss512k -XX:+UseCompressedOops
#
# Para el resto de los tamaños, mirar aqui: 
# https://devcenter.heroku.com/articles/java-support
#
# Para la gestion en general de la memoria en Java:
# https://devcenter.heroku.com/articles/java-memory-issues
#
heroku config:set JAVA_OPTS="-Xms768m -Xmx768m -Xmn192m -Xss512k -XX:+UseCompressedOops" --app $1