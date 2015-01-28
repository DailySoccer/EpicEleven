package actors;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.paypal.core.codec.binary.Base64;
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
import utils.InstanceRole;
import utils.ProcessExec;
import utils.TargetEnvironment;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DailySoccerActors {

    public DailySoccerActors(InstanceRole instanceRole) {

        switch (instanceRole) {
            case DEVELOPMENT_ROLE:
                initDevelopmentRole(TargetEnvironment.LOCALHOST);
                break;
            case WORKER_ROLE:
                initWorkerRole();
                break;
            case WEB_ROLE:
                break;
            default:
                throw new RuntimeException("WTF 5550 instanceRole desconocido");
        }
    }

    public void setTargetEnvironment(TargetEnvironment env) {

        if (env == TargetEnvironment.LOCALHOST) {
            // Obviamente asumimos que: 1.- LOCALHOST es el primer env desde el que se inicializa 2.- Quien nos llama
            // aqui se ocupa de no pasarnos dos veces seguidas el mismo env
            closeRabbitMq();
            initDevelopmentRole(env);
        }
        else {
            // Paramos los actores locales (si los hubiera), para no liar
            stopActors();

            // Si hubiera conexion a un rabbitmq anterior, la matamos
            closeRabbitMq();

            // Iniciamos la nueva conexion al nuevo environment
            initRabbitMQ(env);
        }
    }

    private void stopActors() {
        for (ActorRef actorRef : _actors.values()) {
            try {
                scala.concurrent.Future<Boolean> stopped = akka.pattern.Patterns.gracefulStop(actorRef, Duration.create(10, TimeUnit.SECONDS), PoisonPill.getInstance());
                Await.result(stopped, Duration.create(11, TimeUnit.SECONDS));
            }
            catch (Exception e) {
                Logger.error("WTF 2211 The actor {} wasn't stopped within 10 seconds", actorRef.path().name());
            }
        }
        _actors.clear();
    }

    public void tellToActor(String actorName, Object message) {

        // Damos preferencia a funcionar a traves del conejo encolador
        if (_connection != null) {
            try {
                // Enviamos y no esperamos respuesta, fire and forget
                _channel.basicPublish(_EXCHANGE_NAME, actorName /* QueueName */, null, message.toString().getBytes());
            }
            catch (Exception exc) {
                Logger.error("WTF 3344 {}, {}", actorName, message.toString(), exc);
            }
        }
        // Pero si no hubiera conejo, mandamos el mensaje directamente (asumimos que estamos en TargetEnvironment.LOCALHOST)
        else {
            if (!_actors.containsKey(actorName)) {
                throw new RuntimeException(String.format("WTF 7777 El actor %s no existe", actorName));
            }

            _actors.get(actorName).tell(message, ActorRef.noSender());
        }
    }

    public Object tellToActorAwaitResult(String actorName, Object message) {
        Object response = null;

        if (_connection != null) {
            try {
                // Cola con nombre aleatorio por la que tienen que responder los actores. El consumer tiene el mismo
                // exacto ciclo de vida que la cola
                if (_callbackQueueName == null) {
                    _callbackQueueName = _channel.queueDeclare().getQueue();

                    // https://www.rabbitmq.com/tutorials/tutorial-six-java.html
                    _consumer = new QueueingConsumer(_channel);
                    _channel.basicConsume(_callbackQueueName, true /* autoAck */, _consumer);
                }

                String corrId = java.util.UUID.randomUUID().toString();

                BasicProperties props = new BasicProperties
                                                .Builder()
                                                .correlationId(corrId)
                                                .replyTo(_callbackQueueName)
                                                .build();

                // Mandamos el mensaje...
                _channel.basicPublish(_EXCHANGE_NAME, actorName /* QueueName */, props, message.toString().getBytes());

                // ... Y esperamos a que nos llegue la respuesta. Tenemos que esperar a que nos llegue el correlationId correcto
                while (true) {
                    QueueingConsumer.Delivery delivery = _consumer.nextDelivery();
                    if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                        response = deserialize(delivery.getBody());
                        break;
                    }
                }
            }
            catch (Exception exc) {
                Logger.error("WTF 3374 {}, {}", actorName, message.toString(), exc);
            }
        }
        else {
            if (!_actors.containsKey(actorName)) {
                throw new RuntimeException(String.format("WTF 7778 El actor %s no existe", actorName));
            }

            Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
            scala.concurrent.Future<Object> responseFuture = Patterns.ask(_actors.get(actorName), message, timeout);

            try {
                response = Await.result(responseFuture, timeout.duration());
            }
            catch (Exception exc) {
                Logger.error("WTF 5222 DailySoccerActors excepcion esperando resultado de mensaje {}, actor {}", message, actorName, exc);
            }
        }

        return response;
    }

    private void initDevelopmentRole(TargetEnvironment env) {
        initRabbitMQ(env);
        createActors();
        bindActorsToQueues();
    }

    private void initWorkerRole() {
        initRabbitMQ(TargetEnvironment.LOCALHOST); // LOCALHOST equivale a decirle "no intentes leer de heroku"
        createActors();
        bindActorsToQueues();
        tickActors();
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

    private void tickActors() {
        for (ActorRef actorRef : _actors.values()) {
            actorRef.tell("Tick", ActorRef.noSender());
        }
    }

    private void createActors() {
        _actors.put("OptaProcessorActor", Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor"));
        _actors.put("InstantiateConstestsActor", Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor"));
        _actors.put("GivePrizesActor", Akka.system().actorOf(Props.create(GivePrizesActor.class), "GivePrizesActor"));
        _actors.put("TransactionsActor", Akka.system().actorOf(Props.create(TransactionsActor.class), "TransactionsActor"));
        _actors.put("BotParentActor", Akka.system().actorOf(Props.create(BotParentActor.class), "BotParentActor"));
    }

    private void bindActorsToQueues() {

        // Soportamos que rabbitmq-server este apagado
        if (_connection == null) {
            return;
        }

        try {
            for (Map.Entry<String, ActorRef> entry : _actors.entrySet()) {

                final String queueName = entry.getKey();
                final ActorRef actorRef = entry.getValue();

                // autodelete: If set, the queue is deleted when all consumers have finished using it.
                _channel.queueDeclare(queueName, false /* durable */, false /* exclusive */, true  /* autodelete */, null);
                _channel.basicConsume(queueName, true /* autoAck */, queueName + "Tag", new DefaultConsumer(_channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties props, byte[] body) throws IOException {
                        String routingKey = envelope.getRoutingKey();
                        String msgString = new String(body);
                        String correlationId = props.getCorrelationId();

                        Logger.debug("DailySoccerActors RabbitMQ Message, RoutingKey {}, Message {}", routingKey, msgString);

                        try {
                            // Si hay un correlationId es que el cliente espera respuesta por parte del actor
                            if (correlationId != null) {
                                Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
                                scala.concurrent.Future<Object> response = Patterns.ask(actorRef, msgString, timeout);

                                Object ret = Await.result(response, timeout.duration());

                                BasicProperties replyProps = new BasicProperties
                                                                    .Builder()
                                                                    .correlationId(correlationId)
                                                                    .build();

                                _channel.basicPublish(_EXCHANGE_NAME, props.getReplyTo(), replyProps, serialize(ret));
                            }
                            else {
                                // Si no hay correlationId, podemos hacer fire and forget
                                actorRef.tell(msgString, ActorRef.noSender());
                            }
                        }
                        catch (Exception exc) {
                            Logger.error("WTF 5222 DailySoccerActors mensaje {}, actor {}", msgString, queueName, exc);
                        }
                    }
                });
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 9688 DailySoccerActors no pudo inicializar el consumer de mensajes RabbitMQ", exc);
        }
    }

    private byte[] serialize(Object obj) {
        byte[] serializedObject = null;

        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream so = new ObjectOutputStream(bo);
            so.writeObject(obj);
            so.flush();
            serializedObject = Base64.encodeBase64(bo.toByteArray());
        }
        catch (Exception e) {
            Logger.error("WTF 3222 Error serializando objeto {}", obj.toString());
        }

        return serializedObject;
    }

    private Object deserialize(byte[] src) {
        Object ret = null;
        try {
            ByteArrayInputStream bi = new ByteArrayInputStream(Base64.decodeBase64(src));
            ObjectInputStream si = new ObjectInputStream(bi);
            ret = si.readObject();
        }
        catch (Exception e) {
            Logger.error("WTF 3222 Error deserializando objeto {}", new String(src));
        }
        return ret;
    }

    private void initRabbitMQ(TargetEnvironment env) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(readRabbitMQUriForEnvironment(env));

            _connection = factory.newConnection();
            _channel = _connection.createChannel();

            Logger.debug("RabbitMQ inicializado en TargetEnvironment.{}", env.toString());
        }
        catch (Exception exc) {
            Logger.warn("DailySoccerActors no pudo inicializar RabbitMQ");
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

        _callbackQueueName = null;
        _consumer = null;

        try {
            if (_channel != null) {
                _channel.close();
                _channel = null;
            }
        }
        catch (Exception exc) {
            Logger.debug("WTF 6699 RabbitMQ Channel no pudo cerrar", exc);
        }

        try {
            if (_connection != null) {
                _connection.close();
                _connection = null;
            }
        }
        catch (Exception exc) {
            Logger.debug("WTF 6699 RabbitMQ Connection no pudo cerrar", exc);
        }
    }

    static public void main(String[] args) {
        Application application = new DefaultApplication(new File(args[0]), DailySoccerActors.class.getClassLoader(), null, Mode.Prod());
        Play.start(application);
    }

    Connection _connection;
    Channel _channel;
    String _callbackQueueName;
    QueueingConsumer _consumer;

    HashMap<String, ActorRef> _actors = new HashMap<>();          // Todos los actores que creamos, hasheados por nombre

    final static String _EXCHANGE_NAME = "";    // Default exchange
}
