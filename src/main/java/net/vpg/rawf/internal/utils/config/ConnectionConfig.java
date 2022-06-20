package net.vpg.rawf.internal.utils.config;

import okhttp3.OkHttpClient;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ConnectionConfig {
    private OkHttpClient httpClient;
    private String baseUrl;
    private String userAgent;

    public ConnectionConfig(OkHttpClient httpClient, String baseUrl, String userAgent) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    @Nonnull
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Nonnull

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Nonnull

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
