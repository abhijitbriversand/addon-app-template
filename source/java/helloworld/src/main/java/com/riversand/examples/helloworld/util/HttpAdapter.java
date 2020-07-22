package com.riversand.examples.helloworld.util;

import com.google.gson.JsonObject;
import com.riversand.rsconnect.common.rsconnect.driver.Constants;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;

/**
 * This class performs all http request and response process
 */
public class HttpAdapter {

    public static HttpResponse post(JsonObject req, String url) throws IOException {

        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(url);
        StringEntity postString = new StringEntity(req.toString());
        post.setEntity(postString);
        post.setHeader(Constants.HttpChannel.CONTENTTYPE, "application/json");
        post.setHeader("Accept", "application/json");
        return httpClient.execute(post);
    }
}
