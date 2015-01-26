package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import play.Logger;
import play.api.Application;
import play.api.DefaultApplication;
import play.api.Mode;
import play.api.Play;
import play.libs.Akka;

import java.io.File;

public class DailySoccerActors {

    static public void init(String instanceRole) {

        switch (instanceRole) {
            case "DEVELOPMENT_ROLE":
                initDevelopmentRole();
                break;
            case "WORKER_ROLE":
                initWorkerRole();
                break;
            case "WEB_ROLE":
                break;
            default:
                throw new RuntimeException("WTF 5550 instanceRole desconocido");
        }
    }

    static void initDevelopmentRole() {
        Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor");
        Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor");
        Akka.system().actorOf(Props.create(GivePrizesActor.class), "GivePrizesActor");
        Akka.system().actorOf(Props.create(TransactionsActor.class), "TransactionsActor");
        Akka.system().actorOf(Props.create(BotParentActor.class), "BotParentActor");
    }

    static void initWorkerRole() {

        initRabbitMQ();

        final ActorRef optaProcessorActor = Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor");
        final ActorRef instantiateConstestsActor = Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor");
        final ActorRef givePrizesActor = Akka.system().actorOf(Props.create(GivePrizesActor.class), "GivePrizesActor");
        final ActorRef transactionsActor = Akka.system().actorOf(Props.create(TransactionsActor.class), "TransactionsActor");

        instantiateConstestsActor.tell("Tick", ActorRef.noSender());
        optaProcessorActor.tell("Tick", ActorRef.noSender());
        givePrizesActor.tell("Tick", ActorRef.noSender());
        transactionsActor.tell("Tick", ActorRef.noSender());

        // El sistema de bots solo se tickea bajo demanda (por ejemplo, desde la zona de admin)
        Akka.system().actorOf(Props.create(BotParentActor.class), "BotParentActor");
    }

    static private void initRabbitMQ() {
        final String QUEUE_NAME = "BotParentActor";
        final String EXCHANGE_NAME = "";    // Default exchange

        try {
            String connectionUri = play.Play.application().configuration().getString("rabbitmq", "amqp://guest:guest@localhost");

            ConnectionFactory factory = new ConnectionFactory();
            //factory.setAutomaticRecoveryEnabled(true);
            factory.setUri(connectionUri);
            _connection = factory.newConnection();

            Channel channel = _connection.createChannel();

            channel.queueDeclare(QUEUE_NAME, false /* durable */, false /* exclusive */, false  /* autodelete */, null);
            channel.queuePurge(QUEUE_NAME);

            String message = "StartChildren";
            channel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, null, message.getBytes());
        }
        catch (Exception exc) {
            Logger.debug("RabbitMQ no pudo conectar", exc);
        }
    }

    static public Connection getRabbitMQConnection() {
        return _connection;
    }

    static public void shutdown() {

        try {
            _connection.close();
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

    static Connection _connection;
}
