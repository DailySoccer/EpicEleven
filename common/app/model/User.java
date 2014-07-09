package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;

import java.util.Date;

public class User {
	public String firstName;
	public String lastName;
    public String nickName;
	public String email;
	public String password;
    public Date createdAt;

    public User() {
        createdAt = GlobalDate.getCurrentDate();
    }

	public User(String firstName, String lastName, String nickName, String email, String password) {
        this();
		this.firstName = firstName;
		this.lastName = lastName;
        this.nickName = nickName;
		this.email = email;
		this.password = password;
	}

    /**
     * Query de un usuario por su identificador en mongoDB (verifica la validez del mismo)
     *
     * @param userId Identificador del usuario
     * @return User
     */
    static public User find(String userId) {
        User aUser = null;
        Boolean userValid = ObjectId.isValid(userId);
        if (userValid) {
            aUser = Model.users().findOne(new ObjectId(userId)).as(User.class);
        }
        return aUser;
    }

    @JsonView(JsonViews.NotForClient.class)
    public ObjectId _id;
}