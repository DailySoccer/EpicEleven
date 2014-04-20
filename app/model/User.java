package model;

import com.fasterxml.jackson.annotation.JsonView;
import org.bson.types.ObjectId;

public class User {
	public String firstName;
	public String lastName;
    public String nickName;
	public String email;
	public String password;

    @JsonView(JSONViews.NotForClient.class)
    public ObjectId _id;


	public User(String firstName, String lastName, String nickName, String email, String password)
	{
		this.firstName = firstName;
		this.lastName = lastName;
        this.nickName = nickName;
		this.email = email;
		this.password = password;
	}

    public User() {}
}