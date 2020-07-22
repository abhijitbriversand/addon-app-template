package com.riversand.examples.helloworld.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JsonUtil performs all json related processing
 */
public class JsonUtil {

    private static JsonParser parser = new JsonParser();

    public static JsonObject readFromFile(String filePath) {
        Path path = Paths.get(filePath);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = null;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            jsonObject = parser.parse(reader).getAsJsonObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public static void writeToFile(JsonObject jsonObject, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(jsonObject.toString());
        }
    }

    public static JsonObject httpResponseToJsonObject(HttpResponse response) throws IOException {
        return (JsonObject) parser.parse(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
    }

    public static JsonObject stringToJsonObject(String text) {
        return parser.parse(text).getAsJsonObject();
    }

}
