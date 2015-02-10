package actors;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.BasicProperties;
import play.Logger;
import play.api.Application;
import play.api.DefaultApplication;
import play.api.Mode;
import play.api.Play;
import play.libs.Akka;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import utils.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Actors {

    public Actors(InstanceRole instanceRole) {

        switch (instanceRole) {
            case DEVELOPMENT_ROLE:
                initDevelopmentRole(TargetEnvironment.LOCALHOST);
                break;
            case WORKER_ROLE:
                initWorkerRole();
                break;
            case WEB_ROLE:
                initWebRole();
                break;
            default:
                throw new RuntimeException("WTF 5550 instanceRole desconocido");
        }
    }

    private void initDevelopmentRole(TargetEnvironment env) {
        initRabbitMQ(env);
        createLocalActors();
        bindLocalActorsToQueues();
    }

    private void initWorkerRole() {
        initRabbitMQ(TargetEnvironment.LOCALHOST); // LOCALHOST equivale a decirle "no intentes leer de heroku"
        createLocalActors();
        bindLocalActorsToQueues();
    }

    private void initWebRole() {
        // De momento solo lo necesitamos para el admin en staging, pero tambien querremos en el futuro mandar
        // tareas (mirror de operaciones en posgres por ejemplo) a nuestros actores
        initRabbitMQ(TargetEnvironment.LOCALHOST);
    }

    public void setTargetEnvironment(TargetEnvironment env) {

        if (env == TargetEnvironment.LOCALHOST) {
            // Obviamente asumimos que: 1.- LOCALHOST es el primer env desde el que se inicializa 2.- Quien nos llama
            // aqui se ocupa de no pasarnos dos veces seguidas el mismo env
            closeRabbitMq();
            initDevelopmentRole(env);
        }
        else {
            // Paramos los actores locales (si los hubiera), para no liar. Evitamos bugs como que un actor local
            // tuviera un tick pendiente de ejecutar, lo ejecute con la conexion ya cambiada y de repente mande un
            // mensaje al sistema remoto! (por ejemplo, SimulatorController.Reset :) )
            stopLocalActors();

            // Si hubiera conexion a un rabbitmq anterior, la matamos
            closeRabbitMq();

            // Iniciamos la nueva conexion al nuevo environment
            initRabbitMQ(env);
        }
    }

    public void restartActors() {

        Logger.debug("Restarting Actors");

        if (_connection != null) {
            tell("OptaProcessorActor", "PoisonPill");
            tell("ContestsActor", "PoisonPill");
            tell("TransactionsActor", "PoisonPill");
            tell("BotSystemActor", "PoisonPill");
            tell("SimulatorActor", "PoisonPill");
        } else {
            stopLocalActors();
            createLocalActors();
            bindLocalActorsToQueues();
        }

        Logger.debug("Actors restarted");
    }

    private void stopLocalActors() {
        for (ActorRef actorRef : _localActors.values()) {
            try {
                scala.concurrent.Future<Boolean> stopped = akka.pattern.Patterns.gracefulStop(actorRef, Duration.create(10, TimeUnit.SECONDS), PoisonPill.getInstance());
                Await.result(stopped, Duration.create(11, TimeUnit.SECONDS));
            }
            catch (Exception e) {
                Logger.error("WTF 2211 The actor {} wasn't stopped within 10 seconds", actorRef.path().name());
            }
        }
        _localActors.clear();
    }

    public void tell(String actorName, Object message) {

        // Damos preferencia a funcionar a traves del conejo encolador
        if (_connection != null) {
            try {
                Logger.trace("20 tell {}, {}", actorName, message);

                // Enviamos y no esperamos respuesta, fire and forget
                _directChannel.basicPublish(_EXCHANGE_NAME, actorName /* QueueName */, null, serialize(message));

                Logger.trace("21 tell {}, {}", actorName, message);
            }
            catch (Exception exc) {
                Logger.error("WTF 3344 {}, {}", actorName, message.toString(), exc);
            }
        }
        // Pero si no hubiera conejo, mandamos el mensaje directamente (asumimos que estamos en TargetEnvironment.LOCALHOST)
        else {
            if (!_localActors.containsKey(actorName)) {
                throw new RuntimeException(String.format("WTF 7777 El actor %s no existe", actorName));
            }

            _localActors.get(actorName).tell(message, ActorRef.noSender());
        }
    }

    public <T> T tellAndAwait(String actorName, Object message) {
        Object response = null;

        if (_connection != null) {
            try {
                Logger.trace("0 tellAndAwait {}, {}", actorName, message);

                // Una cola para cada llamada. El correlationId NO es para multiplexar, sino simplemente para soportar
                // un caso oscuro cuando el servidor se reinicia (leer el tutorial sobre RPC). Si usaramos la misma cola,
                // habria que hacer un NACK para que el mensaje vuelva a la cola cuando no lo reconocemos.
                String callbackQueueName = _directChannel.queueDeclare().getQueue();
                String corrId = java.util.UUID.randomUUID().toString();

                BasicProperties props = new BasicProperties
                                                .Builder()
                                                .correlationId(corrId)
                                                .replyTo(callbackQueueName)
                                                .build();

                // Mandamos el mensaje...
                _directChannel.basicPublish(_EXCHANGE_NAME, actorName /* QueueName */, props, serialize(message));

                // Consume el mensaje de la cola. Es por ello por lo que no podemos usar la misma cola siempre.
                QueueingConsumer consumer = new QueueingConsumer(_directChannel);
                _directChannel.basicConsume(callbackQueueName, false, consumer);

                QueueingConsumer.Delivery delivery = consumer.nextDelivery(5000);

                if (delivery != null) {
                    _directChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

                    // Tenemos que verificar que el correlationId es el mismo que mandamos
                    if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                        response = deserialize(delivery.getBody());
                    }
                    else {
                        Logger.error("WTF 2020");
                    }
                }
                else {
                    Logger.error("WTF 2021");
                }
                _directChannel.queueDelete(callbackQueueName);

                Logger.trace("5 tellAndAwait {}, {}", actorName, message);
            }
            catch (InterruptedException exc) {
                Logger.error("WTF 3174 {}, {}", actorName, message.toString(), exc);
            }
            catch (IOException exc) {
                Logger.error("WTF 3374 {}, {}", actorName, message.toString(), exc);
            }
        }
        else {
            if (!_localActors.containsKey(actorName)) {
                throw new RuntimeException(String.format("WTF 7778 El actor %s no existe", actorName));
            }

            Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
            scala.concurrent.Future<Object> responseFuture = Patterns.ask(_localActors.get(actorName), message, timeout);

            try {
                response = Await.result(responseFuture, timeout.duration());
            }
            catch (Exception exc) {
                Logger.error("WTF 5222 {}, esperando resultado de mensaje {}, actor {}", exc.getMessage(), message, actorName);
            }
        }

        return uncheckedCast(response);
    }

    private String readRabbitMQUriForEnvironment(TargetEnvironment env) {
        String ret = play.Play.application().configuration().getString("rabbitmq");

        if (env != TargetEnvironment.LOCALHOST) {
            try {
                ret = ProcessExec.exec("heroku config:get CLOUDAMQP_URL -a " + env.herokuAppName);
            }
            catch (IOException e) {
                Logger.error("WTF 8900 Sin permisos, o sin heroku instalado. Falling back to local.");
            }
        }

        return ret;
    }

    private void createLocalActors() {
        _localActors.put("OptaProcessorActor", Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor"));
        _localActors.put("ContestsActor", Akka.system().actorOf(Props.create(ContestsActor.class), "ContestsActor"));

        _localActors.put("TransactionsActor", Akka.system().actorOf(Props.create(TransactionsActor.class), "TransactionsActor"));
        _localActors.put("NotificationActor", Akka.system().actorOf(Props.create(NotificationActor.class), "NotificationActor"));

        _localActors.put("SimulatorActor", Akka.system().actorOf(Props.create(SimulatorActor.class), "SimulatorActor"));
        _localActors.put("BotSystemActor", Akka.system().actorOf(Props.create(BotSystemActor.class), "BotSystemActor"));
    }

    private void bindLocalActorsToQueues() {

        // Soportamos que rabbitmq-server este apagado
        if (_connection == null) {
            return;
        }

        try {
            for (Map.Entry<String, ActorRef> entry : _localActors.entrySet()) {

                final Channel returnChannel = _connection.createChannel();
                _returnChannels.put(entry.getKey(), returnChannel);

                final String queueName = entry.getKey();
                final ActorRef actorRef = entry.getValue();

                // autodelete: If set, the queue is deleted when all consumers have finished using it.
                returnChannel.queueDeclare(queueName, false /* durable */, false /* exclusive */, true  /* autodelete */, null);
                returnChannel.basicConsume(queueName, false /* autoAck */, queueName + "Tag", new DefaultConsumer(returnChannel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties props, byte[] body) throws IOException {
                        String correlationId = props.getCorrelationId();

                        Logger.trace("1000 handleDelivery {}, {}", envelope.getRoutingKey(), new String(body));

                        try {
                            // Si hay un correlationId es que el cliente espera respuesta por parte del actor
                            if (correlationId != null) {
                                Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
                                scala.concurrent.Future<Object> response = Patterns.ask(actorRef, deserialize(body), timeout);

                                Logger.trace("1001 handleDelivery {}, mensaje {}", queueName, new String(body));
                                Object ret = Await.result(response, timeout.duration());
                                Logger.trace("1002 handleDelivery {}, mensaje {}", queueName, new String(body));

                                BasicProperties replyProps = new BasicProperties
                                        .Builder()
                                        .correlationId(correlationId)
                                        .build();

                                returnChannel.basicPublish(_EXCHANGE_NAME, props.getReplyTo(), replyProps, serialize(ret));

                                Logger.trace("1003 handleDelivery {}, {}", envelope.getRoutingKey(), ret.toString());
                            }
                            else {
                                Logger.trace("2000 handleDelivery SYNC {}, {}", queueName, new String(body));

                                // Si no hay correlationId, podemos hacer fire and forget
                                actorRef.tell(deserialize(body), ActorRef.noSender());

                                Logger.trace("2001 handleDelivery SYNC {}, {}", queueName, new String(body));
                            }
                        }
                        catch (Exception exc) {
                            Logger.error("WTF 5222 Actors mensaje {}, actor {}", new String(body), queueName, exc);
                        }

                        returnChannel.basicAck(envelope.getDeliveryTag(), false);
                    }
                });
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 9688 Actors no pudo inicializar el consumer de mensajes RabbitMQ", exc);
        }
    }

    private byte[] serialize(Object obj) {

        // Usamos un sobre para asegurar que soportamos cualquier tipo de dato. MessageEnvelope tiene su params anotado para
        // que Jackson incluya el nombre de la clase y por lo tanto la pueda deserializar al otro lado del cable.
        MessageEnvelope envelope = new MessageEnvelope("Envelope", obj);
        String json = null;

        try {
            json = new ObjectMapper().writeValueAsString(envelope);
        }
        catch (Exception e) {
            Logger.error("WTF 3222 Error serializando objeto {}", obj.toString(), e);
        }

        return json.getBytes();
    }

    private Object deserialize(byte[] src) {

        MessageEnvelope ret = null;

        try {
            // Permitimos fallar en Unknow Properties pq nuestros mensajes entre actores pueden tener getters sin setters
            ret = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                    .readValue(new String(src), new TypeReference<MessageEnvelope>() {
                                    });

            Logger.trace("deserialize {}", new String(src));

            if (!ret.msg.equals("Envelope")) {
                Logger.error("WTF 9991");
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 2229 Error deserializando", exc);
        }

        return ret.params;
    }

    private void initRabbitMQ(TargetEnvironment env) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(readRabbitMQUriForEnvironment(env));

            _connection = factory.newConnection();
            _directChannel = _connection.createChannel();

            _connection.addBlockedListener(new BlockedListener() {
                public void handleBlocked(String reason) throws IOException {
                    Logger.debug("RabbitMq: Connection is now blocked");
                }

                public void handleUnblocked() throws IOException {
                    Logger.debug("RabbitMq: Connection is now unblocked");
                }
            });

            Logger.info("RabbitMq inicializado en TargetEnvironment.{}", env.toString());
        }
        catch (Exception exc) {
            Logger.warn("Actors no pudo inicializar RabbitMq");
        }
    }

    public void shutdown() {

        closeRabbitMq();

        // Esto para todos los actores y metodos scheduleados.
        // Logeara "Shutdown application default Akka system.". Y si no has hecho el init, lo inicializa ahora!
        Akka.system().shutdown();

        // Hacemos un 'join' para asegurar que no matamos el modelo estando todavia procesando
        Akka.system().awaitTermination();
    }

    private void closeRabbitMq() {

        try {
            for (Channel channel : _returnChannels.values()) {
                channel.close();
            }
            _returnChannels.clear();

            if (_directChannel != null) {
                _directChannel.close();
                _directChannel = null;
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 6699 RabbitMq exception cerrando canales", exc);
        }

        try {
            if (_connection != null) {
                _connection.close();
                _connection = null;
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 6699 RabbitMq Connection no pudo cerrar", exc);
        }
    }


    static public void main(String[] args) {
        Application application = new DefaultApplication(new File(args[0]), Actors.class.getClassLoader(), null, Mode.Prod());
        Play.start(application);
    }

    /**
     * Helps to avoid using {@code @SuppressWarnings({"unchecked"})} when casting to a generic type.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> T uncheckedCast(Object obj) {
        return (T) obj;
    }


    Connection _connection;
    Channel _directChannel;

    HashMap<String, ActorRef> _localActors = new HashMap<>();           // Todos los actores que creamos, hasheados por nombre
    HashMap<String, Channel> _returnChannels = new HashMap<>();         // 1 canal por actor. 1 dia buscando un deadlock.

    final static String _EXCHANGE_NAME = "";    // Default exchange
}
