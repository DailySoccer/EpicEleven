package model.stormpath;

import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.ApplicationList;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.tenant.Tenant;


public class StormPathClient {


    String path = "apiKey.properties";

    ApiKey apiKey = ApiKeys.builder().setFileLocation(path).build();
    Client client = Clients.builder().setApiKey(apiKey).build();

    Tenant tenant = client.getCurrentTenant();

    ApplicationList myApplications = client.getApplications();





}
