package utils;

public enum TargetEnvironment {
    LOCALHOST(""),
    STAGING("dailysoccer-staging"),
    PRODUCTION("dailysoccer");

    public String herokuAppName;

    private TargetEnvironment(String appName) {
        herokuAppName = appName;
    }
}
