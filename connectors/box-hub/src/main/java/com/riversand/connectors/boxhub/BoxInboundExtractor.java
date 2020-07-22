package com.riversand.connectors.boxhub;

import java.io.IOException;

import com.google.gson.JsonObject;

import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.event.IEventManager;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.common.helpers.GsonBuilder;
import com.riversand.rsconnect.interfaces.clients.IServiceClient;
import com.riversand.rsconnect.interfaces.extractors.IRecordExtractor;
import com.riversand.rsconnect.interfaces.managers.IPersistenceManager;
import com.riversand.rsconnect.interfaces.metrics.ITaskMetrics;
import com.riversand.rsconnect.interfaces.models.IRecord;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.Box.FILE_UPLOADED;
import static com.riversand.connectors.extension.helpers.Constants.Box.ITEM_STATUS;
import static com.riversand.connectors.extension.helpers.Constants.Box.SPLIT;
import static com.riversand.connectors.extension.helpers.Constants.Box.TRIGGER;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7820;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.NAME;
import static com.riversand.connectors.extension.helpers.Constants.SOURCE;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;

public class BoxInboundExtractor extends BaseBox implements IRecordExtractor {

   private static ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(BoxInboundExtractor.class);

   public BoxInboundExtractor(String data, RSConnectContext connectContext, IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client) throws IOException {
      this(connectContext.getExecutionContext().getTaskId(), data, connectContext, persistenceManager, metrics, client, null);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Initialized BoxInboundExtractor");
   }

   public BoxInboundExtractor(RSConnectContext connectContext, IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient client) throws IOException {
      this(connectContext.getExecutionContext().getTaskId(), null, connectContext, persistenceManager, metrics, client, null);

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Initialized BoxInboundExtractor");
   }

   /**
    * Constructor.
    *
    * @param data               the identifier associated with this task.
    * @param connectContext     describing the context of the input.
    * @param persistenceManager Persist data in RDP.
    * @param metrics            Metrics gathering object.
    */
   public BoxInboundExtractor(String taskId, String data, RSConnectContext connectContext,
                              IPersistenceManager persistenceManager, ITaskMetrics metrics, IServiceClient serviceClient, IEventManager eventManager)
         throws IOException {

      super(taskId, data, connectContext, persistenceManager, metrics, serviceClient, eventManager);
   }

   @Override
   public IRecord next() throws IOException {
      try {
         pmLogger.entry();
         pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "BoxInboundExtractor next()");

         JsonObject responseJsonObject = GsonBuilder.getGsonInstance().fromJson(jsonReader, JsonObject.class);

         String trigger = JsonRecord.getValue(responseJsonObject, TRIGGER);

         if (FILE_UPLOADED.equalsIgnoreCase(trigger)) {
            JsonObject sourceObject = JsonRecord.findObject(responseJsonObject, SOURCE);

            String fileName = JsonRecord.getValue(sourceObject, NAME);

            String[] fileNameArray = fileName.split("\\.");

            String entityId = null;

            if (fileNameArray.length > 1) {
               fileName = fileNameArray[0];
            }

            fileNameArray = fileName.split(SPLIT);

            if (fileNameArray.length > 1) {
               entityId = fileName.split(SPLIT)[1];
            }

            String itemStatus = JsonRecord.getValue(sourceObject, ITEM_STATUS);

            if ("active".equalsIgnoreCase(itemStatus) && entityId != null) {
               updateConnectorStateForReleasedEntity(entityId);
            }
         }
      } catch (Exception e) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Exception: [%s], StackTrace: [%s]", e.getMessage(), e.getStackTrace()));
         throw new ConnectRuntimeException(RSC_7820, e, e.getCause().getMessage());
      } finally {
         pmLogger.exit();
      }

      return null;
   }
}
