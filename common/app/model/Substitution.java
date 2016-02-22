package model;

import org.bson.types.ObjectId;

public class Substitution {
    public ObjectId source;
    public ObjectId target;

    public Substitution() {}

    public Substitution(ObjectId source, ObjectId target) {
        this.source = source;
        this.target = target;
    }
}


