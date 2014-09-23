package model.stormpath;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.tenant.Tenant;



public class StormPathClient {


    String path = "apiKey.properties";

    ApiKey apiKey = ApiKeys.builder().setFileLocation(path).build();
    Client client = Clients.builder().setApiKey(apiKey).build();

    Tenant tenant = client.getCurrentTenant();



    com.stormpath.sdk.application.Application application = client.instantiate(Application.class);

    application.setName("My Awesome Application"); //must be unique among your other apps
    application = client.createApplication(
            Applications.newCreateRequestFor(application).createDirectory().build());


    //Create the account object
    Account account = client.instantiate(Account.class);

    //Set the account properties
    account.setGivenName("Joe");
    account.setSurname("Stormtrooper");
    account.setUsername("tk421"); //optional, defaults to email if unset
    account.setEmail("tk421@stormpath.com");
    account.setPassword("Changeme1");
    CustomData customData = account.getCustomData();
    customData.put("favoriteColor", "white");

//Create the account using the existing Application object
    application.createAccount(account);
}
