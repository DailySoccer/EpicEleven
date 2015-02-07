package actors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

//
// Para pasar un mensaje desde/hacia el sistema de actores que no sea de un tipo basico (String, int, List<>...) hace
// falta usar esta clase. Su mision es incluir el tipo del mensaje incrustado as a WRAPPER_OBJECT para poderlo mandar
// a traves de RabbitMq y que el serializador pueda deserializarlo.
//
// Para tipos basicos, no hace falta.
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
