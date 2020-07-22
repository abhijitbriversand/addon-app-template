package com.riversand.connectors.boxhub;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import com.riversand.connectors.extension.helpers.CommonHelper;
import com.riversand.connectors.extension.helpers.ConnectorServiceHelper;
import com.riversand.connectors.extension.helpers.ConnectorStateAndActivity;
import com.riversand.connectors.extension.helpers.Constants.Box;
import com.riversand.connectors.extension.helpers.DataObjectHelper;
import com.riversand.connectors.extension.helpers.StringUtils;
import com.riversand.connectors.extension.helpers.TaskSummarizationHelper;
import com.riversand.connectors.extension.base.BaseHub;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.Profile;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.event.IEventManager;
import com.riversand.rsconnect.common.helpers.AuthTokenCache;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.common.helpers.IAuthTokenCache;
import com.riversand.rsconnect.common.services.InMemoryPersistenceManager;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.managers.IPersistenceManager;
import com.riversand.rsconnect.interfaces.metrics.ITaskMetrics;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.BACKSLASH;
import static com.riversand.connectors.extension.helpers.Constants.Box.CONTENT;
import static com.riversand.connectors.extension.helpers.Constants.Box.ENTERPRISE_ID;
import static com.riversand.connectors.extension.helpers.Constants.Box.FILE;
import static com.riversand.connectors.extension.helpers.Constants.Box.FILES;
import static com.riversand.connectors.extension.helpers.Constants.Box.FILE_TYPE_EXTENSION;
import static com.riversand.connectors.extension.helpers.Constants.Box.MAX_FILE_SIZE;
import static com.riversand.connectors.extension.helpers.Constants.Box.PARENT;
import static com.riversand.connectors.extension.helpers.Constants.Box.PARENT_FOLDER;
import static com.riversand.connectors.extension.helpers.Constants.Box.PASSPHRASE_KEY;
import static com.riversand.connectors.extension.helpers.Constants.Box.PRIVATE_KEY;
import static com.riversand.connectors.extension.helpers.Constants.Box.PUBLIC_KEY_ID;
import static com.riversand.connectors.extension.helpers.Constants.Box.SPLIT;
import static com.riversand.connectors.extension.helpers.Constants.ConnectorService.UPDATE;
import static com.riversand.connectors.extension.helpers.Constants.ERROR_RECORD_COUNT;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.ASSERTION_PROPERTY;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.AUTH_URL;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.CLIENT_ID_PROPERTY;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.CLIENT_SECRET_PROPERTY;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.GRANT_TYPE_PROPERTY;
import static com.riversand.connectors.extension.helpers.Constants.ID;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7820;
import static com.riversand.connectors.extension.helpers.Constants.POSTREQUESTMETHOD;
import static com.riversand.connectors.extension.helpers.Constants.PROCESSED_RECORD_COUNT;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.ASSET_URL;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.ATTRIBUTES;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.TYPE;
import static com.riversand.connectors.extension.helpers.Constants.VALUE;
import static com.riversand.rsconnect.common.rsconnect.driver.Constants.EventAttributes.FILE_ID;
import static com.riversand.rsconnect.common.rsconnect.driver.Constants.EventAttributes.FILE_NAME;
import static com.riversand.rsconnect.common.rsconnect.driver.Constants.NAME;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorMessage.SYNDICATION_MESSAGE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorState.CHANNEL_STATE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorState.SYNDICATION_STATE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorState.SYNDICATION_STATE_DATE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorState.VALIDATION_STATE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorState.VALIDATION_STATE_DATE;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorStatus.COMPLETED;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorStatus.ERROR;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorStatus.IN_PROCESS;
import static com.riversand.rsconnect.interfaces.constants.Constants.ConnectorStatus.LISTED_PENDING;
import static com.riversand.rsconnect.interfaces.constants.Constants.Connectors.ENTITY_ID;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;

public class BaseBox extends BaseHub {
   private static final ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(BaseBox.class);
   private static final IAuthTokenCache authTokenCache = AuthTokenCache.getInstance();

   //Extractor Properties
   Profile profile;
   int counter;
   JsonReader jsonReader;
   boolean hasSuccessRecord;
   List<String> errorRecords;

   //Loader Properties
   String workerId;
   String parentId;
   IPersistenceManager persistenceManager;
   String stateObjectId;
   JsonArray entities;

   public BaseBox(String taskId,
                  RSConnectContext connectContext,
                  ITaskMetrics metrics,
                  IServiceClient client, IEventManager eventManager) throws IOException {
      super(taskId, connectContext, metrics, client, eventManager);

      if (Strings.isNullOrEmpty(syndicationSettings.getChannelId())) {
         throw new ConnectRuntimeException(RSC_7820, "channelId not found in publish-additionalsettings at BaseBox");
      }

      syndicationSettings.setHubName(ConnectorServiceHelper.getHubName(syndicationSettings.getChannelId(), tenantId));
      if (Strings.isNullOrEmpty(syndicationSettings.getHubName())) {
         throw new ConnectRuntimeException(RSC_7820, "hubName not found in channelSettings");
      }

      setChannelInformation();

      entities = new JsonArray();
   }

   public BaseBox(RSConnectContext connectContext, IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client) throws IOException {
      this(connectContext.getExecutionContext().getTaskId(), null, connectContext, persistenceManager, metrics, client, null);
      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Connect Profile at BaseBox: %s", connectContext.getConnectProfile().toString()));

      if (Strings.isNullOrEmpty(syndicationSettings.getChannelId())) {
         throw new ConnectRuntimeException(RSC_7820, "channelId not found in publish-additionalsettings at BaseBox");
      }
   }

   public BaseBox(String taskId, String data, RSConnectContext connectContext,
                  IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client, IEventManager eventManager) throws IOException {
      this(taskId, connectContext, metrics, client, eventManager);

      profile = connectContext.getConnectProfile();

      // Either get data from persistent storage or from serialized input directly.
      if (this.profile.getCollect().isBinaryStreamPersistent() || persistenceManager instanceof InMemoryPersistenceManager) {
         if (Strings.isNullOrEmpty(data)) {
            data = this.connectContext.getExecutionContext().getFileId();
         }
         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "input stream reading started");
         IPersistenceManager.InputResource inputResource = persistenceManager.getInputStream(data);
         this.jsonReader = new JsonReader(new InputStreamReader(inputResource.Stream, StandardCharsets.UTF_8));
      }

      // Support both single json object and array of json objects.
      if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
         this.jsonReader.beginArray();
      }

      cacheAccessToken();
   }

   @Override
   protected void setChannelInformation() throws IOException {
      super.setChannelInformation();

      setHubInformation();

      setFileInformation();
   }

   @Override
   protected void setHubInformation() {
      super.setHubInformation();
      validateHubInformation();
      try {
         if (channelInfo != null && !channelInfo.isEmpty()) {
            syndicationSettings.setAuthurl(channelInfo.get(AUTH_URL));
            if (Strings.isNullOrEmpty(syndicationSettings.getAuthurl())) {
               throw new ConnectRuntimeException(RSC_7820, "authurl not found in configs at BaseBox");
            }
            syndicationSettings.setPublicKeyId(channelInfo.get(PUBLIC_KEY_ID));
            if (Strings.isNullOrEmpty(syndicationSettings.getPublicKeyId())) {
               throw new ConnectRuntimeException(RSC_7820, "publicKeyID not found in configs at BaseBox");
            }
            syndicationSettings.setPrivateKey(channelInfo.get(PRIVATE_KEY));
            if (Strings.isNullOrEmpty(syndicationSettings.getPrivateKey())) {
               throw new ConnectRuntimeException(RSC_7820, "privateKey not found in configs at BaseBox");
            }
            syndicationSettings.setPassphrase(channelInfo.get(PASSPHRASE_KEY));
            if (Strings.isNullOrEmpty(syndicationSettings.getPassphrase())) {
               throw new ConnectRuntimeException(RSC_7820, "passphrase not found in configs at BaseBox");
            }
            syndicationSettings.setEnterpriseId(channelInfo.get(ENTERPRISE_ID));
            if (Strings.isNullOrEmpty(syndicationSettings.getEnterpriseId())) {
               throw new ConnectRuntimeException(RSC_7820, "enterpriseID not found in configs at BaseBox");
            }
         }
      } catch (Exception ex) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Exception at setHubInformation: %s", ex.getMessage()), ex);
      }
   }

   private void setFileInformation() {
      syndicationSettings.setParentFolder(channelInfo.get(PARENT_FOLDER));
      if (Strings.isNullOrEmpty(syndicationSettings.getParentFolder())) {
         syndicationSettings.setParentFolder("0");
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7820, "parentFolder not found in configs at BaseBox, defaulting it to 0");
      }
      syndicationSettings.setMaxFileSize(channelInfo.get(MAX_FILE_SIZE));
      if (Strings.isNullOrEmpty(syndicationSettings.getMaxFileSize())) {
         syndicationSettings.setMaxFileSize("100000");
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7820, "maxFileSize not found in configs at BaseBox, defaulting it to 100000");
      }
   }

   protected String cacheAccessToken() {
      String token = null;
      try {
         JsonObject reqBody = new JsonObject();
         reqBody.addProperty(GRANT_TYPE_PROPERTY, Box.GRANT_TYPE_VALUE);
         reqBody.addProperty(CLIENT_ID_PROPERTY, syndicationSettings.getClientId());
         reqBody.addProperty(CLIENT_SECRET_PROPERTY, syndicationSettings.getSecret());
         reqBody.addProperty(ASSERTION_PROPERTY, BoxHelper.getAssertion(syndicationSettings, tenantId));

         token = BoxHelper.getAccessToken(syndicationSettings.getAuthurl(), reqBody, tenantId);
         if (token != null && authTokenCache != null) {
            authTokenCache.put(syndicationSettings.getClientId(), token);
            pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Cache token updated");
         }
      } catch (Exception ex) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, ex);
      }
      return token;
   }

   public boolean hasNext() throws IOException {
      try {
         return jsonReader.hasNext();
      } catch (Exception ex) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, ex);
         return false;
      }
   }

   public int count() {
      return counter;
   }

   public void close() throws IOException {
      if (jsonReader != null) {
         jsonReader.close();
         jsonReader = null;
      }

      if (!errorRecords.isEmpty()) {
         if (!hasSuccessRecord) {
            TaskSummarizationHelper.updateTaskSummarizationObject(eventManager, connectContext, taskId, -1, errorRecords.size(), errorRecords.size(), true);
         } else {
            TaskSummarizationHelper.cacheProperty(connectContext.getConnectProfile().getPublish().getFormat(), taskId, ERROR_RECORD_COUNT, String.valueOf(counter));
         }
      }
      TaskSummarizationHelper.cacheProperty(connectContext.getConnectProfile().getPublish().getFormat(), taskId, PROCESSED_RECORD_COUNT, String.valueOf(counter));
   }

   public byte[] flush() {
      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "BaseBox flush called");

      updateTaskSummarization();

      return new byte[0];
   }

   void processEntityJson(String reqId, String hubId, JsonObject entitiesJson) throws IOException {
      String entityId = JsonRecord.getValue(entitiesJson, ID);

      String entityType = JsonRecord.getValue(entitiesJson, TYPE);
      StringBuilder errors = new StringBuilder();
      ConnectorStateAndActivity connectorMessageActivity = ConnectorServiceHelper.getConnectorMessageActivity(reqId, entityId, syndicationSettings.getChannelId(), connectContext);

      if (stateObjectId == null) {
         throw new ConnectRuntimeException(RSC_7820, "StateObject Object not found for Box");
      }

      hubId = processUpload(constructUrl(hubId), entitiesJson, errors, connectorMessageActivity);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Box uploaded item id is %s ", hubId));

      updateConnectorState(entityId, entityType, hubId, errors);

      updateTaskSummarization();
   }

   private String constructUrl(String hubId) {
      StringBuilder requestURL = new StringBuilder();

      requestURL.append(syndicationSettings.getBaseurl());
      requestURL.append(BACKSLASH);
      requestURL.append(FILES);
      requestURL.append(BACKSLASH);

      if (!Strings.isNullOrEmpty(hubId)) {
         requestURL.append(hubId);
         requestURL.append(BACKSLASH);
      }

      requestURL.append(CONTENT);

      return requestURL.toString();
   }

   private String processUpload(String requestURL, JsonObject entitiesJson, StringBuilder errors, ConnectorStateAndActivity connectorMessageActivity) throws IOException {
      return processEntity(requestURL, entitiesJson, errors, connectorMessageActivity);
   }

   public void updateConnectorState(String entityId, String entityType, String hubId, StringBuilder apiError) throws IOException {

      ConnectorStateAndActivity connectorState = ConnectorServiceHelper.setConnectorStateProperties(hubId, entityId, entityType, syndicationSettings.getChannelId(), stateObjectId, UPDATE, connectContext);
      setConnectorState(apiError.toString(), connectorState);
      //Updating item validation state
      ConnectorServiceHelper.processConnectorState(connectorState);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Connector State updated with state attributes");
   }

   void setConnectorState(String apiError, ConnectorStateAndActivity connectorState) {
      Map<String, String> stateMap = new HashMap<>();
      final String date = CommonHelper.getCurrentDate(tenantId);
      if (StringUtils.isNullOrEmpty(apiError)) {
         stateMap.put(CHANNEL_STATE, LISTED_PENDING);
         stateMap.put(SYNDICATION_STATE, COMPLETED);
         stateMap.put(VALIDATION_STATE, COMPLETED);
      } else {
         stateMap.put(SYNDICATION_STATE, ERROR);
         stateMap.put(VALIDATION_STATE, IN_PROCESS);
         stateMap.put(SYNDICATION_MESSAGE, apiError);
      }
      stateMap.put(VALIDATION_STATE_DATE, date);
      stateMap.put(SYNDICATION_STATE_DATE, date);

      connectorState.setStateMap(stateMap);
   }

   String processEntity(String requestURL, JsonObject entitiesJson, StringBuilder apiError, ConnectorStateAndActivity connectorMessageActivity) throws IOException {
      String reqId = createConnectorStateAndActivity(connectorMessageActivity, "Uploading to Box");

      String assetURL = DataObjectHelper.getAttributeValue(entitiesJson, ASSET_URL);

      byte[] file = BoxHelper.downloadBlob(assetURL, Integer.parseInt(syndicationSettings.getMaxFileSize()));

      JsonArray requestQuery = new JsonArray();

      JsonObject formData = new JsonObject();

      JsonObject parent = new JsonObject();
      JsonRecord.setValue(parent, ID, syndicationSettings.getParentFolder());

      String entityId = JsonRecord.getValue(entitiesJson, ID);

      JsonObject attributeObject = new JsonObject();

      StringBuilder fileName = new StringBuilder();

      fileName.append(tenantId);
      fileName.append(SPLIT);
      fileName.append(entityId);

      String fileType = DataObjectHelper.getAttributeValue(entitiesJson, FILE_TYPE_EXTENSION);

      if (!Strings.isNullOrEmpty(fileType)) {
         fileName.append(".");
         fileName.append(fileType);
      }

      JsonRecord.setValue(attributeObject, NAME, fileName.toString());

      attributeObject.add(PARENT, parent);

      JsonRecord.setValue(formData, NAME, ATTRIBUTES);
      JsonRecord.setValue(formData, VALUE, attributeObject);
      JsonRecord.setValue(formData, FILE_ID, FILE);
      JsonRecord.setValue(formData, FILE_NAME, entityId);

      requestQuery.add(formData);

      String hubId = BoxHelper.sendHubRequest(requestURL, requestQuery, file, POSTREQUESTMETHOD, getAccessToken(), apiError, tenantId);

      updateConnectorStateAndActivity(JsonRecord.getValue(entitiesJson, ENTITY_ID), connectorMessageActivity, reqId, hubId);

      return hubId;
   }
}
