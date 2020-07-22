package com.riversand.connectors.boxhub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import com.riversand.connectors.extension.helpers.CommonHelper;
import com.riversand.connectors.extension.helpers.ConnectorServiceHelper;
import com.riversand.connectors.extension.helpers.DataObjectHelper;
import com.riversand.connectors.extension.helpers.TaskSummarizationHelper;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.event.EventManager;
import com.riversand.rsconnect.common.event.IEventManager;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.common.helpers.GsonBuilder;
import com.riversand.rsconnect.common.services.InMemoryPersistenceManager;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.extractors.IRecordExtractor;
import com.riversand.rsconnect.interfaces.managers.IPersistenceManager;
import com.riversand.rsconnect.interfaces.metrics.ITaskMetrics;
import com.riversand.rsconnect.interfaces.models.IRecord;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7820;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.ASSET_URL;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.ID;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.REL_TO_ID;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.REL_TO_TYPE;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.TYPE;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;

public class BoxOutboundExtractor extends BaseBox implements IRecordExtractor {

   private static ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(BoxOutboundExtractor.class);

   public BoxOutboundExtractor(String data, RSConnectContext connectContext, IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client) throws IOException {
      this(connectContext.getExecutionContext().getTaskId(), data, connectContext, persistenceManager, metrics, client, null);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Initialized BoxOutboundExtractor");
   }

   public BoxOutboundExtractor(RSConnectContext connectContext, IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client) throws IOException {
      this(connectContext.getExecutionContext().getTaskId(), null, connectContext, persistenceManager, metrics, client, null);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Initialized BoxOutboundExtractor");
   }

   /**
    * Constructor.
    *
    * @param data               the identifier associated with this task.
    * @param connectContext     describing the context of the input.
    * @param persistenceManager Persist data in RDP.
    * @param metrics            Metrics gathering object.
    */
   public BoxOutboundExtractor(String taskId, String data, RSConnectContext connectContext,
                               IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient serviceClient, IEventManager eventManager)
         throws IOException {

      super(taskId, data, connectContext, persistenceManager, metrics, serviceClient, eventManager);

      /**
       * Get multiple images from relationship array and process one image at a time
       */
      JsonObject jsonObject = GsonBuilder.getGsonInstance().fromJson(jsonReader, JsonObject.class);
      if (!jsonObject.isJsonNull()) {
         JsonArray relationshipArray = CommonHelper.getRelationshipArray(jsonObject, syndicationSettings, syndicationSettings.getImageof(), tenantId);
         JsonArray imageEntitiesArray = new JsonArray();

         for (JsonElement relationshipElement : relationshipArray) {
            if (relationshipElement != null && relationshipElement.isJsonObject()) {
               String relToId = JsonRecord.getValue(relationshipElement, REL_TO_ID);
               String relToType = JsonRecord.getValue(relationshipElement, REL_TO_TYPE);
               imageEntitiesArray.add(CommonHelper.getEntityObject(relToId, relToType, client, tenantId));
            }
         }

         IPersistenceManager imageEntitiesPersistenceManager = new InMemoryPersistenceManager(imageEntitiesArray.toString().getBytes(Charset.defaultCharset()), new ByteArrayOutputStream());
         IPersistenceManager.InputResource inputResource = imageEntitiesPersistenceManager.getInputStream(data);
         this.jsonReader = new JsonReader(new InputStreamReader(inputResource.Stream, StandardCharsets.UTF_8));
      }

      // Support both single json object and array of json objects.
      if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
         this.jsonReader.beginArray();
      }
   }

   @Override
   public IRecord next() throws IOException {
      String entityId = null;
      pmLogger.entry();
      try {
         JsonObject jsonObject = GsonBuilder.getGsonInstance().fromJson(jsonReader, JsonObject.class);

         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Input json in Box Outbound Extract : [%s]", jsonObject.toString()));

         metrics.setCurrentRecordId(DataObjectHelper.generateObjectId());
         metrics.addToRecordCount(1);
         counter++;

         entityId = JsonRecord.getValue(jsonObject, ID);
         if (entityId == null) {
            throw new ConnectRuntimeException(RSC_7820, "Entity Id not found in the payload at Box Extractor");
         }
         String entityType = JsonRecord.getValue(jsonObject, TYPE);
         if (entityType == null) {
            throw new ConnectRuntimeException(RSC_7820, "Entity type not found in the payload at the Box Extractor");
         }

         Map<String, String> entityIdAndTypes = new HashMap<>();

         entityIdAndTypes.put(entityId, entityType);

         Map<String, String> assetUrls = CommonHelper.getAssetUrls(connectContext, client, tenantId, entityIdAndTypes);

         ConnectorServiceHelper.addToAttribute(jsonObject, ASSET_URL, assetUrls.get(entityId), connectContext);

         ConnectorServiceHelper.processConnectorState(jsonObject, null, null, syndicationSettings, connectContext);

         ConnectorServiceHelper.saveRequestActivity(connectContext, jsonObject, DataObjectHelper.generateObjectId());

         hasSuccessRecord = true;

         pmLogger.debug(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Exiting Box Extract : [%s]", jsonObject.toString()));

         return new JsonRecord(jsonObject, metrics.getCurrentRecordId());
      } catch (Exception e) {
         if (!Strings.isNullOrEmpty(entityId) && !errorRecords.contains(entityId)) {
            TaskSummarizationHelper.updateTaskSummarizationObject(eventManager, connectContext, taskId, true, false);
            errorRecords.add(entityId);
         }
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, e.getMessage(), e);
         TaskSummarizationHelper.generateErrorEvent(eventManager, connectContext, tenantId, taskId, connectContext.getExecutionContext().getParentTaskId(), e.getMessage(), EventManager.BatchStage.EXTRACT);
      } finally {
         pmLogger.exit();
      }
      return null;
   }
}
