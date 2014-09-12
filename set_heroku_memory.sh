#!/bin/sh

echo ''
echo "  \033[0;31mESTO ES SOLO UNA REFERENCIA!!!!\033[0m"
echo '  Para que sea facil recordar como cambiar las opciones de memoria de la JVM.'
echo ''
echo "  No ejecutar, en los JAVA_OPTS reales puede haber mas parametros, y este comando los machacaria!"
echo ''
echo '  # $1 es el nombre de la app => dailysoccer, dailysoccer-staging'
echo ''
echo '  En una instancia 1x en el primer push crea un JAVA_OPTS con -Xmx384m -Xss512k -XX:+UseCompressedOops'
echo ''
echo '  Para el resto de los tamaños, mirar aqui: https://devcenter.heroku.com/articles/java-support'
echo ''
echo '  Para la gestion en general de la memoria en Java: https://devcenter.heroku.com/articles/java-memory-issues'
echo ''
echo '  \033[1;33mheroku config:set JAVA_OPTS="-Xms768m -Xmx768m -Xmn192m -Xss512k -XX:+UseCompressedOops" --app $1'
echo ''