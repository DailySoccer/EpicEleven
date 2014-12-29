package actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import play.libs.Akka;

import play.api.DefaultApplication;
import play.api.Play;
import play.api.Mode;
import play.api.Application;

import java.io.File;

public class DailySoccerActors {

    //
    // En un principio esto iba a ser un singleton y usabamos nuestro propio ActorSystem, no el de Akka.system().
    //
    // Deciamos entonces:
    //
    // Si el simulador fuera tambien un actor, no nos haria falta esto porque desde 'el mandariamos
    // mensajes al resto de actores a traves del context. Pero no es asi de momento, asi que necesitamos mantener
    // una referencia a nuestros actores en algun sitio. Otra cosa que tambien lo evita es el remoting.
    //
    // El remoting en cualquier caso parece que lo vamos a necesitar, y quiza entonces sea buen momento para levantar
    // nuestro propio ActorSystem.
    //
    static public void init(boolean bIsWorker) {

        final ActorRef instantiateConstestsActor = Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor");
        final ActorRef optaProcessorActor = Akka.system().actorOf(Props.create(OptaProcessorActor.class), "OptaProcessorActor");

        if (bIsWorker) {
            instantiateConstestsActor.tell("Tick", ActorRef.noSender());
            optaProcessorActor.tell("Tick", ActorRef.noSender());
        }
    }

    static public void shutdown() {
        // Esto para todos los actores y metodos scheduleados
        Akka.system().shutdown();

        // Hacemos un 'join' para asegurar que no matamos el modelo estando todavia procesando
        Akka.system().awaitTermination();
    }

    static public void main(String[] args) {
        Application application = new DefaultApplication(new File(args[0]), DailySoccerActors.class.getClassLoader(), null, Mode.Prod());
        Play.start(application);
    }
}
