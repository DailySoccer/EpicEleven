package actors;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.rabbitmq.client.*;
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

import java.io.File;
import java.io.IOException;
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
            // aqui se ocupa de no pasarnos nos veces seguidas el mismo env
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
            } catch (Exception e) {
                Logger.error("WTF 2211 The actor {} wasn't stopped within 10 seconds", actorRef.path().name());
            }
        }
        _actors.clear();
    }

    public void tellToActor(String actorName, Object message) {

        // Damos preferencia a funcionar a traves del conejo encolador
        if (_connection != null) {
            try {
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
        Object ret = null;

        if (_connection != null) {
            // TODO... RPC
        }
        else {
            if (!_actors.containsKey(actorName)) {
                throw new RuntimeException(String.format("WTF 7778 El actor %s no existe", actorName));
            }

            Timeout timeout = new Timeout(scala.concurrent.duration.Duration.create(5, TimeUnit.SECONDS));
            scala.concurrent.Future<Object> response = Patterns.ask(_actors.get(actorName), message, timeout);

            try {
                ret = Await.result(response, timeout.duration());
            }
            catch (Exception exc) {
                Logger.error("WTF 5222 DailySoccerActors excepcion esperando resultado de mensaje {}, actor {}", message, actorName, exc);
            }
        }

        return ret;
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
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                        String routingKey = envelope.getRoutingKey();
                        String msgString = new String(body);
                        Logger.debug("DailySoccerActors RabbitMQ Message, RoutingKey {}, Message {}", routingKey, msgString);
                        actorRef.tell(msgString, ActorRef.noSender());
                    }
                });
            }
        }
        catch (Exception exc) {
            Logger.error("WTF 9688 DailySoccerActors no pudo inicializar el consumer de mensajes RabbitMQ", exc);
        }
    }

    private void initRabbitMQ(TargetEnvironment env) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(readRabbitMQUriForEnvironment(env));

            _connection = factory.newConnection();
            _channel = _connection.createChannel();
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

    HashMap<String, ActorRef> _actors = new HashMap<>();          // Todos los actores que creamos, hasheados por nombre

    final static String _EXCHANGE_NAME = "";    // Default exchange
}
