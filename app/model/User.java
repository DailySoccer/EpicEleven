package model;

import javax.persistence.*;

import play.db.ebean.*;

@Entity
public class User extends Model {
	public String firstName;
	public String lastName;
	public String email;
	public String password;
	
	public User(String firstName, String lastName, String email, String password)
	{
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.password = password;
	}
}