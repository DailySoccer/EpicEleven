package model;

// The client made a mistake. This doesn't persist to the DB.
public class ClientError {
    public String error;
    public String description;

    public ClientError(String error, String desc) { this.error = error; this.description = desc; }
}
