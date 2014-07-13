package model;

import org.bson.types.ObjectId;

/**
 * Interfaz usado para poder obtener el "ObjectId" de un Pojo (en un metodo "generic")
 */
public interface JongoId {
    public ObjectId getId();
}
