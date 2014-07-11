package model;

import org.bson.types.ObjectId;

/**
 * Interfaz usado para que Snapshot puede obtener el "ObjectId" de una clase (en un metodo "generic")
 */
public interface JongoId {
    public ObjectId getId();
}
