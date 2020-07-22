package com.riversand.examples.helloworld.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riversand.rsconnect.common.config.AppConfig;
import com.riversand.rsconnect.common.rsconnect.driver.Constants;
import org.apache.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlatformUtil {
    private static final Logger LOGGER = LogManager.getLogger(PlatformUtil.class.getName());
    private static final String SOURCE_CONFIG_NAME = "dataplatformpodconfig_template.json";
    private static final String TARGET_CONFIG_NAME = "dataplatformpodconfig.json";
    private static final String TENANT_CONFIG_NAME = "tenantserviceconfig.json";
    private static final String TENANT_CONFIG_REQUEST = "tenantserviceconfigrequest.json";

    private static final String POD_ID = "@@POD_ID@@";
    private static final String SOURCE_CONFIG_PATH = "@@SOURCE_CONFIG_PATH@@";
    private static final String TENANT_ID = "@@TENANT_ID@@";
    private static final String GET_TENANT_CONFIG_URL = "dataplatform/api/configurationservice/get";

    public static void preparePlatform() {
        String sourceConfigFile = Config.SOURCE_CONFIG_PATH == null ? "./" + TARGET_CONFIG_NAME : Config.SOURCE_CONFIG_PATH + TARGET_CONFIG_NAME;
        String sourceConfigPath = Config.SOURCE_CONFIG_PATH == null ? "./" : Config.SOURCE_CONFIG_PATH;
        String tenantServiceConfigFile = Config.SOURCE_CONFIG_PATH == null ? "./" + TENANT_CONFIG_NAME : Config.SOURCE_CONFIG_PATH + TENANT_CONFIG_NAME;
        String tenantConfigReqFile = Config.SOURCE_CONFIG_PATH == null ? "./" + TENANT_CONFIG_REQUEST : Config.SOURCE_CONFIG_PATH + TENANT_CONFIG_REQUEST;

        // prepare configuration
        prepareDataPlatformPdConfiguration(sourceConfigPath);
        getTenantServiceConfig(tenantConfigReqFile, tenantServiceConfigFile);
        AppConfig.setConfigFilePath(sourceConfigFile);
        AppConfig.getInstance().setRDPHostBase(Config.RDP_COMPLETE_URL);
    }

    private static void prepareDataPlatformPdConfiguration(String sourceConfigPath) {
        LOGGER.info("Start making {} ", TARGET_CONFIG_NAME);
        Path sourcePath = Paths.get(sourceConfigPath + SOURCE_CONFIG_NAME);
        Path targetPath = Paths.get(sourceConfigPath + TARGET_CONFIG_NAME);
        try (Stream<String> lines = Files.lines(sourcePath)) {
            List<String> replaced = lines.map(line -> line.replaceAll(POD_ID, Config.POD_ID)
                    .replaceAll(TENANT_ID, Config.TENANT)
                    .replaceAll(SOURCE_CONFIG_PATH, sourceConfigPath))
                    .collect(Collectors.toList());
            Files.write(targetPath, replaced);
        } catch (IOException e) {
            LOGGER.info("Problem occurred when preparing pod configuration {}", e.getMessage());
        }
    }

    private static void resetRestEventSending(JsonObject jsonObject) {
        for (String key : jsonObject.keySet()) {
            JsonElement val = jsonObject.get(key);
            if (val.isJsonObject()) {
                resetRestEventSending(val.getAsJsonObject());
            } else if (key.equals("isEventRestCallsEnabled")) {
                jsonObject.addProperty("isEventRestCallsEnabled", "true");
            }
        }
    }

    private static void getTenantServiceConfig(String requestFilePath, String targetFilePath) {
        LOGGER.info("Start making {} ", targetFilePath);
        JsonObject req = JsonUtil.readFromFile(requestFilePath);
        JsonObject tenantReqObject = req.get(Constants.OPERATION_SEARCH_PARAMS).getAsJsonObject().get(Constants.OPERATION_SEARCH_QUERY).getAsJsonObject();
        tenantReqObject.remove(Constants.ID);
        tenantReqObject.addProperty(Constants.ID, Config.TENANT);
        try {
            HttpResponse response = HttpAdapter.post(req, Config.RDP_COMPLETE_URL + GET_TENANT_CONFIG_URL);
            JsonObject tenantServiceConfigObject = JsonUtil.httpResponseToJsonObject(response).get(Constants.RESPONSE).getAsJsonObject().get(Constants.CONFIG_OBJECTS).getAsJsonArray().get(0).getAsJsonObject();
            resetRestEventSending(tenantServiceConfigObject);
            JsonUtil.writeToFile(tenantServiceConfigObject, targetFilePath);

        } catch (IOException e) {
            LOGGER.error("Problem occurred while retrieving tenant configuration", e);
        }
    }

    public static JsonObject getAuthContext() {

        JsonObject authContext = new JsonObject();
        authContext.addProperty("Content-Type", "application/json");
        authContext.addProperty("x-rdp-version", "8.1");
        authContext.addProperty("x-rdp-clientId", "rdpclient");
        authContext.addProperty("x-rdp-tenantId", Config.TENANT);
        authContext.addProperty("x-rdp-ownershipData", "business");
        authContext.addProperty("x-rdp-userId", "mary.jane@riversand.com");
        authContext.addProperty("x-rdp-userName", "Maryj");
        authContext.addProperty("x-rdp-lastName", "Jane");
        authContext.addProperty("x-rdp-userEmail", "meet.mehta@riversand.com");
        authContext.addProperty("x-rdp-userRoles", Constants.Api.SYSTEM_USER_ROLE);
        return authContext;
    }
}
