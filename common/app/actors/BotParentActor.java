package actors;


import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class BotParentActor extends UntypedActor {

    @Override public void preStart() throws Exception {
        super.preStart();

        for (int c = 0; c < 10; ++c) {
            getContext().actorOf(Props.create(BotActor.class, c), String.format("BotActor%d", c));
        }
    }


    @Override
    public void onReceive(Object msg) {
        switch ((String) msg) {
        }
    }
}
