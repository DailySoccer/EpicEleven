package actors;


import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.Logger;

public class BotParentActor extends UntypedActor {

    @Override
    public void onReceive(Object msg) {
        switch ((String) msg) {
            case "StartChildren":
                if (!_childrenStarted) {
                    Logger.debug("BotParentActor arrancando bots hijos");

                    for (int c = 0; c < 10; ++c) {
                        getContext().actorOf(Props.create(BotActor.class, c), String.format("BotActor%d", c));
                    }

                    _childrenStarted = true;
                }
                else {
                    Logger.error("WTF 1567 Recibido mensaje a destiempo");
                }

                sender().tell(_childrenStarted, getSelf());

                break;
            case "StopChildren":
                if (_childrenStarted) {
                    Logger.debug("BotParentActor parando bots hijos");

                    // Since stopping an actor is asynchronous, you cannot immediately reuse the name of the child you just stopped;
                    // this will result in an InvalidActorNameException. Instead, watch the terminating actor and create its
                    // replacement in response to the Terminated message which will eventually arrive.
                    // gracefulStop is useful if you need to wait for termination.
                    // http://doc.akka.io/docs/akka/2.3.8/java/untyped-actors.html
                    for (ActorRef child : getContext().getChildren()) {
                        getContext().stop(child);
                    }

                    _childrenStarted = false;
                }
                else {
                    Logger.error("WTF 1560 Recibido mensaje a destiempo");
                }

                sender().tell(_childrenStarted, getSelf());

                break;

            case "ChildrenStarted":
                sender().tell(_childrenStarted, getSelf());
                break;
        }
    }

    boolean _childrenStarted;
}
