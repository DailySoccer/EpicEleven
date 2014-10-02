package actors;

import akka.actor.*;
import play.libs.Akka;

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

        final ActorRef insActor = Akka.system().actorOf(Props.create(InstantiateContestsActor.class), "InstantiateConstestsActor");

        if (bIsWorker) {
            insActor.tell("Start", ActorRef.noSender());
        }
    }

    static public void shutdown() {
        // Esto para todos los actores y metodos scheduleados
        Akka.system().shutdown();

        // Hacemos un 'join' para asegurar que no matamos el modelo estando todavia procesando
        Akka.system().awaitTermination();
    }


    private DailySoccerActors() {

    }
}
