package stormpath;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.AccountStatus;
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
            Logger.error("Stormpath DISCONNECTED");
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
            _connected = _myApp!=null;
        }
    }

    public boolean isConnected() {
        return _connected;
    }

    public F.Tuple<Integer, String> register(String givenName, String email, String password) { //, ObjectId userId) {
        Account account = _client.instantiate(Account.class);

        account.setGivenName(givenName);
        account.setSurname("Stormtrooper");
        account.setUsername(givenName); //optional, defaults to email if unset
        account.setEmail(email);
        account.setPassword(password);

        CustomData customData = account.getCustomData();
        customData.put("Verified", false);

        //Aquí es donde realmente se crea el usuario (se envía a StormPath)
        try {
            _myApp.createAccount(account);
            account.setStatus(AccountStatus.ENABLED);
            account.save();
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()) +"\n"+
                         String.valueOf(ex.getCode()) +"\n"+
                         ex.getMessage() +"\n"+
                         ex.getMoreInfo());

            return new F.Tuple<>(ex.getCode(), ex.getMessage());

        }
        return new F.Tuple<>(-1, "");
    }


    public F.Tuple<Integer, String> askForPasswordReset(String email) {
        try {
            _myApp.sendPasswordResetEmail(email);
        }
        catch (ResourceException ex) {
            Logger.error(String.valueOf(ex.getStatus()) +"\n"+
                         String.valueOf(ex.getCode()) +"\n"+
                         ex.getMessage() +"\n"+
                         ex.getMoreInfo());

            return new F.Tuple<>(ex.getCode(), ex.getMessage());
        }
        return new F.Tuple<>(-1, "");
    }

    public F.Tuple<Integer, String> verifyAccountToken(String token) {
        try {
            Account myAccount = _client.getCurrentTenant().verifyAccountEmail(token);

            CustomData customData = myAccount.getCustomData();
            customData.put("Verified", true);
            myAccount.save();
        }
        catch (ResourceException ex) {
            Logger.error(ex.getMessage());
            Logger.error(ex.getMoreInfo());

            return new F.Tuple<>(ex.getCode(), ex.getMessage());
        }
        return new F.Tuple<>(-1, "");
    }

    public F.Tuple<Integer, String> verifyPasswordResetToken(String token) {
        try {
            _myApp.verifyPasswordResetToken(token);
        }
        catch (ResourceException ex) {
            Logger.error(ex.getMessage() +"\n"+
                         ex.getMoreInfo());

            return new F.Tuple<>(ex.getCode(), ex.getMessage());
        }
        return new F.Tuple<>(-1, "");
    }

    public F.Tuple<Account, F.Tuple<Integer, String>> resetPasswordWithToken(String token, String password) {
        Account account;
        try {
            account = _myApp.resetPassword(token, password);
        }
        catch (ResourceException ex) {
            Logger.error(ex.getMessage() +"\n"+
                         ex.getMoreInfo());

            return new F.Tuple<>(null, new F.Tuple<>(ex.getCode(), ex.getMessage()));
        }
        return new F.Tuple<>(account, new F.Tuple<>(-1, ""));
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
            Logger.error(ex.getMessage() +"\n"+
                         ex.getMoreInfo());
        }
        return null;
    }

    public Account login(String usernameOrEmail, String rawPassword){
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
                // Will output: "Invalid username or password."
                Logger.error(ex.getMessage() +"\n"+
                             ex.getMoreInfo());
            }

        }
        return null;
    }

    public F.Tuple<Integer, String> changeUserProfile(String userEmail, String firstName, String lastName, String nickName, String email) {

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
                try {
                    usersAccount.save();
                } catch (ResourceException ex) {
                    // Will output: "Invalid username or password."
                    Logger.error(ex.getMessage() +"\n"+
                                 ex.getMoreInfo());

                    return new F.Tuple<>(ex.getCode(), ex.getMessage());
                }
            }
        }
        else {
            return new F.Tuple<>(991, "El cambio en el perfil ha fallado.");
        }
        return new F.Tuple<>(-1, "");
    }

    public F.Tuple<Integer, String> updatePassword(String userEmail, String password) {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("email", userEmail);
        AccountList accounts = _myApp.getAccounts(queryParams);

        Account usersAccount = null;
        for (Account account: accounts) {
            usersAccount = account;
        }

        if (usersAccount != null) {
            usersAccount.setPassword(password);
            try {
                usersAccount.save();
            }
            catch (ResourceException ex) {
                // Will output: "Invalid username or password."
                Logger.error(ex.getMessage() +"\n"+
                             ex.getMoreInfo());

                return new F.Tuple<>(ex.getCode(), ex.getMessage());
            }
        }
        else {
            return new F.Tuple<>(992, "El cambio de contraseña ha fallado");
        }
        return new F.Tuple<>(-1, "");
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
    Application _myApp;
    static StormPathClient _instance;
    boolean _connected = false;
}
