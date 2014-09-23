package model.stormpath;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationList;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;
import org.bson.types.ObjectId;
import play.Logger;


public class StormPathClient {

    public StormPathClient() {

        ApplicationList applications = _tenant.getApplications();

        for (Application app : applications) {
            if (app.getName().equals("DFS")) {
                _myApp = app;
            }
        }

        if (_myApp == null) {
            _myApp = _client.instantiate(Application.class);

            _myApp.setName("DFS"); //must be unique among your other apps
            _myApp = _client.createApplication(
                    Applications.newCreateRequestFor(_myApp).createDirectory().build());
        }
    }

    public void register(String givenName, String email, String password) { //, ObjectId userId) {
        //Create the account object
        Account account = _client.instantiate(Account.class);

        //Set the account properties
        account.setGivenName(givenName);
        account.setSurname("Stormtrooper");
        account.setUsername(givenName); //optional, defaults to email if unset
        account.setEmail(email);
        account.setPassword(password);

        //CustomData customData = account.getCustomData();
        //customData.put("userId", userId.toString());

        //Create the account using the existing Application object
        _myApp.createAccount(account);
    }

    public Account login(String usernameOrEmail, String rawPassword){
        //Create an authentication request using the credentials
        AuthenticationRequest request = new UsernamePasswordRequest(usernameOrEmail, rawPassword);

        //Now let's authenticate the account with the application:
        try {
            AuthenticationResult result = _myApp.authenticateAccount(request);
            return result.getAccount();
        } catch (ResourceException ex) {
            Logger.error(ex.getStatus() + " " + ex.getMessage());
        }
    }


    ApiKey _apiKey = ApiKeys.builder().setFileLocation("./apiKey.properties").build();
    Client _client = Clients.builder().setApiKey(_apiKey).build();

    Tenant _tenant = _client.getCurrentTenant();
    Application _myApp = null;


}
