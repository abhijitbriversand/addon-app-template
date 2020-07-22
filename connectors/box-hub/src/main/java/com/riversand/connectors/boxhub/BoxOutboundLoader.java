package com.riversand.connectors.boxhub;

import java.io.IOException;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;

import com.riversand.connectors.extension.helpers.ConnectorServiceHelper;
import com.riversand.connectors.extension.helpers.DataObjectHelper;
import com.riversand.connectors.extension.helpers.TaskSummarizationHelper;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.event.EventManager;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.loaders.IRecordLoaderV2;
import com.riversand.rsconnect.interfaces.managers.IPersistenceManager;
import com.riversand.rsconnect.interfaces.metrics.ITaskMetrics;
import com.riversand.rsconnect.interfaces.models.IRecord;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.ConnectorService.CACHE_KEY;
import static com.riversand.connectors.extension.helpers.Constants.ConnectorService.CONNECTOR_REQUEST_ID;
import static com.riversand.connectors.extension.helpers.Constants.ConnectorService.STATEOBJECT_ID;
import static com.riversand.connectors.extension.helpers.Constants.ERROR_RECORD_COUNT;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;

public class BoxOutboundLoader extends BaseBox implements IRecordLoaderV2 {

   private static ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(BoxOutboundLoader.class);

   public BoxOutboundLoader(String workerId,
                            String taskId,
                            String parentId, RSConnectContext connectContext,
                            IPersistenceManager persistenceManager,
                            ITaskMetrics metrics,
                            IServiceClient client) throws IOException {
      super(taskId, connectContext, metrics, client, new EventManager(client, connectContext));

      this.workerId = workerId;
      this.parentId = parentId;
      this.persistenceManager = persistenceManager;

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Initialized BoxOutboundLoader with taskid : %s", taskId));
   }

   @Override
   public byte[] load(IRecord record) throws Exception {
      pmLogger.entry();
      try {
         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Box Product load started");
         JsonRecord entitiesJsonRecord = (JsonRecord) record;
         JsonObject entitiesJson = entitiesJsonRecord.getJsonObject();
         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Transformed entitiesJson at Box Loader: %s", entitiesJson.toString()));

         entities.add(entitiesJson);

         stateObjectId = DataObjectHelper.getAttributeValue(entitiesJson, STATEOBJECT_ID);
         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("StateObjectId at BoxOutboundLoader is %s", stateObjectId));
         String key = DataObjectHelper.getAttributeValue(entitiesJson, CACHE_KEY);
         String reqId = DataObjectHelper.getAttributeValue(entitiesJson, CONNECTOR_REQUEST_ID);

         String hubItemId = null;
         if (!Strings.isNullOrEmpty(stateObjectId)) {
            hubItemId = extractSubmissionId(ConnectorServiceHelper.getConnectorStateObject(stateObjectId, key, connectContext.getExecutionContext()));
            pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Existing Box Id %s", hubItemId));
         }

         processEntityJson(reqId, hubItemId, entitiesJson);

         if (Strings.isNullOrEmpty(reqId)) {
            pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("request id is empty. entity json: %s", entitiesJson));
         } else {
            updateRequestActivity(reqId);
         }
      } catch (Exception ex) {
         pmLogger.error(connectContext.getExecutionContext().getTenantId(), RSCONNECT_SERVICE, RSC_7273, syndicationSettings.getBaseurl(), ex);
         TaskSummarizationHelper.cacheRecordCount(connectContext.getConnectProfile().getPublish().getFormat(), taskId, ERROR_RECORD_COUNT);
         TaskSummarizationHelper.updateTaskSummarizationObject(eventManager, connectContext, taskId, false, true);
         //added to capture the error details in task for export
         TaskSummarizationHelper.generateErrorEvent(eventManager, connectContext, tenantId, taskId, parentId, ex.getMessage(), EventManager.BatchStage.LOAD);
      } finally {
         pmLogger.exit();
      }
      return new byte[0];
   }
}
