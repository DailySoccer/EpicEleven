package model.stormpath;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
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
import com.stormpath.sdk.provider.ProviderAccountRequest;
import com.stormpath.sdk.provider.ProviderAccountResult;
import com.stormpath.sdk.provider.Providers;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;
import play.Logger;
import play.Play;
import play.libs.F;

import java.util.HashMap;
import java.util.Map;


public class StormPathClient {

    private static final String APPLICATION_NAME = "EpicEleven";

    public StormPathClient() {

        ApiKey apiKey = null;
        _client = null;
        _myApp = null;

        try {
            if (Play.isDev()) {
                apiKey = ApiKeys.builder().setFileLocation("./conf/apiKey.properties").build();
            }
            else {
                apiKey = ApiKeys.builder().setId(Play.application().configuration().getString("stormpath.id"))
                        .setSecret(Play.application().configuration().getString("stormpath.secret"))
                        .build();
            }
        }
        catch (Exception e) {
            Logger.info("Stormpath DISCONNECTED");
        }

        if (apiKey != null) {
            _client = Clients.builder().setApiKey(apiKey).build();

            Tenant _tenant = _client.getCurrentTenant();

            ApplicationList applications = _tenant.getApplications();

            for (Application app : applications) {
                if (app.getName().equals(APPLICATION_NAME)) {
                    _myApp = app;
                }
            }

            if (_myApp == null) {
                _myApp = _client.instantiate(Application.class);

                _myApp.setName(APPLICATION_NAME); //must be unique among your other apps
                _myApp = _client.createApplication(
                        Applications.newCreateRequestFor(_myApp).createDirectory().build());
            }
        }

    }

    public String register(String givenName, String email, String password) { //, ObjectId userId) {
        if (_client == null) {
            return null;
        }

        Account account = _client.instantiate(Account.class);

        account.setGivenName(givenName);
        account.setSurname("Stormtrooper");
        account.setUsername(givenName); //optional, defaults to email if unset
        account.setEmail(email);
        account.setPassword(password);

        //CustomData customData = account.getCustomData();
        //customData.put("userId", userId.toString());

        //Aquí es donde realmente se crea el usuario (se envía a StormPath)
        try {
            _myApp.createAccount(account);
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()));
            Logger.error(String.valueOf(ex.getCode()));
            Logger.error(ex.getMessage());
            Logger.error(ex.getStatus() + " " + ex.getMessage());

            return ex.getMessage();
        }
        return null;
    }


    public String askForPasswordReset(String email) {
        if (_client == null) {
            return null;
        }

        try {
            _myApp.sendPasswordResetEmail(email);
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()));
            Logger.error(String.valueOf(ex.getCode()));
            Logger.error(ex.getMessage());
            Logger.error(ex.getStatus() + " " + ex.getMessage());

            return ex.getMessage();
        }
        return null;
    }

    public String verifyPasswordResetToken(String token) {
        if (_client == null) {
            return null;
        }

        try {
            _myApp.verifyPasswordResetToken(token);
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()));
            Logger.error(String.valueOf(ex.getCode()));
            Logger.error(ex.getMessage());
            Logger.error(ex.getStatus() + " " + ex.getMessage());

            return ex.getMessage();
        }
        return null;
    }

    public F.Tuple<Account, String> resetPasswordWithToken(String token, String password) {
        if (_myApp == null) {
            return null;
        }

        Account account;
        try {
            account = _myApp.resetPassword(token, password);
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()));
            Logger.error(String.valueOf(ex.getCode()));
            Logger.error(ex.getMessage());
            Logger.error(ex.getStatus() + " " + ex.getMessage());

            return new F.Tuple<>(null, ex.getMessage());
        }
        return new F.Tuple<>(account, null);
    }

    public Account facebookLogin(String accessToken){
        try {
            ProviderAccountRequest request = Providers.FACEBOOK.account()
                    .setAccessToken(accessToken)
                    .build();

            ProviderAccountResult result = _myApp.getAccount(request);
            return result.getAccount();
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()));
            Logger.error(String.valueOf(ex.getCode()));
            Logger.error(ex.getMessage());
            Logger.error(ex.getStatus() + " " + ex.getMessage());
        }
        return null;
    }

    public Account login(String usernameOrEmail, String rawPassword){
        if (_myApp == null) {
            return null;
        }

        //Create an authentication request using the credentials
        AuthenticationRequest request = new UsernamePasswordRequest(usernameOrEmail, rawPassword);

        //Now let's authenticate the account with the application:
        try {
            AuthenticationResult authenticationResult = _myApp.authenticateAccount(request);
            return authenticationResult.getAccount();
        }
        catch (ResourceException ex) {
            if (ex.getStatus() == 400 && ex.getCode() == 400 &&
                ex.getMessage().equals("Invalid username or password.")) {
                Logger.info("Invalid login for {}", usernameOrEmail);
            }
            else {
                Logger.error(String.valueOf(ex.getStatus())); // Will output: 400
                Logger.error(String.valueOf(ex.getCode())); // Will output: 400
                Logger.error(ex.getMessage()); // Will output: "Invalid username or password."{
                Logger.error(ex.getStatus() + " " + ex.getMessage());
            }

        }
        return null;
    }

    public String changeUserProfile(String userEmail, String firstName, String lastName, String nickName, String email) {
        if (_myApp == null) {
            return null;
        }

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("email", userEmail);
        AccountList accounts = _myApp.getAccounts(queryParams);

        Account usersAccount = null;
        for (Account account: accounts) {
            usersAccount = account;
        }

        if (usersAccount != null) {
            CustomData customData = _client.getResource(usersAccount.getCustomData().getHref(), CustomData.class);
            customData.put("nickname", nickName);
            customData.save();

            if (usersAccount.getDirectory().getProvider().getProviderId().equals("stormpath")) {
                usersAccount.setGivenName(firstName);
                usersAccount.setSurname(lastName);
                usersAccount.setEmail(email);
                usersAccount.setUsername(nickName);
                usersAccount.save();
            }
        }
        else {
            return "Users profile changes failed";
        }
        return null;
    }

    public String updatePassword(String userEmail, String password) {
        if (_myApp == null) {
            return null;
        }

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("email", userEmail);
        AccountList accounts = _myApp.getAccounts(queryParams);

        Account usersAccount = null;
        for (Account account: accounts) {
            usersAccount = account;
        }

        if (usersAccount != null) {
            usersAccount.setPassword(password);
            usersAccount.save();
        }
        else {
            return "Password change failed";
        }
        return null;
    }

    public CustomData getCustomDataForAccount(Account account) {
        return _client.getResource(account.getCustomData().getHref(), CustomData.class);
    }


    public static StormPathClient instance() {
        if (_instance == null) {
            _instance = new StormPathClient();
        }
        return _instance;
    }

    Client _client;

    Application _myApp = null;

    static StormPathClient _instance;


}
