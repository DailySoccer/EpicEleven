package actors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

//
// Usamos esta clase cuando para comunicar actores queremos un mensaje con parametros, para que no nos haga falta definir
// una clase por cada mensaje que haya.
//
// Incluimos el tipo del mensaje incrustado as a WRAPPER_OBJECT para poderlo mandarlo mandar a traves de RabbitMq y que
// el serializador pueda deserializarlo.
//
//
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.PROPERTY, property = "type")
public class MessageEnvelope {
    final public String msg;

    @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.WRAPPER_OBJECT)
    final public Object params;

    public MessageEnvelope(@JsonProperty("msg") String m, @JsonProperty("params") Object p) { msg = m; params = p; }

    @Override public String toString() {
        return "{MessageEnvelope " + msg + " " + params.toString() + "}";
    }
}
