package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rabbitmq.client.*;
import play.Logger;
import play.api.Application;
import play.api.DefaultApplication;
import play.api.Mode;
import play.api.Play;
import play.libs.Akka;
import utils.InstanceRole;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DailySoccerActors {

    static public DailySoccerActors instance() {
        if (_instance == null) {
            _instance = new DailySoccerActors();
        }
        return _instance;
    }

    public void init(InstanceRole instanceRole) {

        switch (instanceRole) {
            case DEVELOPMENT_ROLE:
                initRabbitMQ();
                createActors();
                bindActorsToQueues();
                break;
            case WORKER_ROLE:
                initRabbitMQ();
                createActors();
                bindActorsToQueues();
                tickActors();
                break;
            case WEB_ROLE:
                break;
            default:
                throw new RuntimeException("WTF 5550 instanceRole desconocido");
        }
    }

    private void tickActors() {
        // El sistema de bots ignorara el mensaje (no conoce el mensaje "Tick), solo se inicializa bajo demanda
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

    private void initRabbitMQ() {
        try {
            String connectionUri = play.Play.application().configuration().getString("rabbitmq", "amqp://guest:guest@localhost");

            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(connectionUri);
            _connection = factory.newConnection();
            _channel = _connection.createChannel();
        }
        catch (Exception exc) {
            Logger.warn("DailySoccerActors no pudo inicializar RabbitMQ");
        }
    }

    public void shutdown() {

        try {
            if (_connection != null) {
                _connection.close();
            }
        }
        catch (Exception exc) {
            Logger.debug("WTF 6699 RabbitMQ no pudo cerrar", exc);
        }

        // Esto para todos los actores y metodos scheduleados. No es necesario mirar bIsWorker
        // (aunque logeara "Shutdown application default Akka system.". Y si no has hecho el init, lo inicializa ahora!)
        Akka.system().shutdown();

        // Hacemos un 'join' para asegurar que no matamos el modelo estando todavia procesando
        Akka.system().awaitTermination();
    }

    static public void main(String[] args) {
        Application application = new DefaultApplication(new File(args[0]), DailySoccerActors.class.getClassLoader(), null, Mode.Prod());
        Play.start(application);
    }

    Connection _connection;
    Channel _channel;

    HashMap<String, ActorRef> _actors = new HashMap<>();          // Todos los actores que creamos, hasheados por nombre

    static DailySoccerActors _instance;
    final static String _EXCHANGE_NAME = "";    // Default exchange
}
