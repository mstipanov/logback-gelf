package com.github.pukkaone.gelf.protocol;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author mstipanov
 * @since 02.12.2015.
 */
public class GelfHTTPSender extends GelfSender {

    private AsyncHttpClient asyncHttpClient;
    private boolean shutdown;
    private String url;

    public GelfHTTPSender(String url) throws IOException {
        this.url = url;
    }

    public boolean sendMessage(GelfMessage message) {
        if (shutdown || !message.isValid()) {
            return false;
        }

        try {
            getAsyncHttpClient().preparePost(url).setBody(message.toJson()).addHeader("Content-Type", "application/json").setBodyEncoding(StandardCharsets.UTF_8.name()).execute(new AsyncCompletionHandler<Object>() {
                @Override
                public Object onCompleted(Response response) throws Exception {
                    //TODO log on error!
                    return null;
                }
            });

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private AsyncHttpClient getAsyncHttpClient() {
        if (null != asyncHttpClient) {
            return asyncHttpClient;
        }
        asyncHttpClient = new AsyncHttpClient();
        return asyncHttpClient;
    }

    public void close() {
        shutdown = true;
        try {
            if (asyncHttpClient != null) {
                asyncHttpClient.close();
            }
        } catch (Exception e) {
            // Ignore exception closing socket.
        } finally {
            asyncHttpClient = null;
        }
    }
}
