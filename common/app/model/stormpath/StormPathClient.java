package model.stormpath;

import com.stormpath.sdk.client.*;

public class StormPathClient {


    String path = "apiKey.properties";

    ApiKey apiKey = ApiKeys.builder().setFileLocation(path).build();
    Client client = Clients.builder().setApiKey(apiKey).build();

}
