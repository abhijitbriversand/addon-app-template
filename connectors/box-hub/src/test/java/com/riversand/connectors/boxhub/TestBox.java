package com.riversand.connectors.boxhub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.common.Strings;
import org.junit.Ignore;
import org.junit.Test;

import com.google.gson.JsonObject;

import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.rsconnect.common.config.RSConnectContext;
import com.riversand.rsconnect.common.helpers.GsonBuilder;
import com.riversand.rsconnect.common.helpers.RSExtensionConnectContextSerializer;
import com.riversand.rsconnect.common.serviceClient.ServiceClient;
import com.riversand.rsconnect.common.services.InMemoryPersistenceManager;
import com.riversand.rsconnect.interfaces.managers.IPersistenceManager;
import com.riversand.rsconnect.interfaces.metrics.ITaskMetrics;
import com.riversand.rsconnect.interfaces.models.IRecord;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class TestBox {
   private RSConnectContext connectContext;
   JsonObject entityData;
   IPersistenceManager persistenceManager;
   String inputResource;
   String profile;

   @Ignore("ignored as this is integration test case")
   @Test
   public void testBoxOutbound() throws Exception {
      profile = "BoxOutboundProfile.json";
      inputResource = "Entity.json";
      initialize();

      /**
       * Extractor:
       * In extractor we Cache Access Token and update the Entity.
       * We also createRequestActivity and Connector state for the
       * entity. Connector State is used to track the status of the
       * entity and also holds submissionId's information.
       */
      ITaskMetrics metrics = mock(ITaskMetrics.class);

      BoxOutboundExtractor extractor = new BoxOutboundExtractor(connectContext, persistenceManager, metrics, null);

      IRecord record = null;
      while (extractor.hasNext()) {
         record = extractor.next();

         assertNotNull(record);

         /**
          * Transform:
          * The extractor passes and IRecord to Transform.
          * In Box's case we use Null Transform, so the IRecord
          * that's generated during extract phase can directly be
          * used in Loader
          */

         /**
          * Loader:
          * The loader processes the information in IRecord,
          * gets cached access token and constructs request
          * to be sent to Box. Once we get a successful response
          * we update Connector State's submission id with Box's
          * uploaded itemID. We also update request activity
          */
         String taskId = UUID.randomUUID().toString();
         String workerID = UUID.randomUUID().toString();
         String parentId = UUID.randomUUID().toString();

         ServiceClient client = new ServiceClient();

         BoxOutboundLoader boxOutboundLoader = new BoxOutboundLoader(workerID, taskId, parentId, connectContext, persistenceManager, metrics, client);
         try {
            boxOutboundLoader.load(record);
            boxOutboundLoader.flush();
         } catch (Exception e) {
            e.printStackTrace();
         }
         ProfilerManager.deactivateProfiler();
      }
   }

   @Ignore("ignored as this is integration test case")
   @Test
   public void testBoxInbound() throws IOException {
      profile = "BoxInboundProfile.json";
      inputResource = "BoxInboundResponse.json";
      initialize();

      ITaskMetrics metrics = mock(ITaskMetrics.class);

      BoxInboundExtractor extractor = new BoxInboundExtractor(connectContext, persistenceManager, metrics, null);

      IRecord record = null;

      for (int i = 0; i < 1; i++) {
         record = extractor.next();
      }

      assertNull(record);
   }

   private void initialize() throws IOException {
      InputStream inputStream = this.getClass().getResourceAsStream(profile);
      connectContext = RSExtensionConnectContextSerializer.fromJson(null, inputStream);
      if (!Strings.isNullOrEmpty(inputResource)) {
         inputStream = this.getClass().getResourceAsStream(inputResource);
         String requestInputStream = IOUtils.toString(inputStream, Charset.defaultCharset());
         entityData = GsonBuilder.getGsonInstance().fromJson(requestInputStream, JsonObject.class);
         persistenceManager = new InMemoryPersistenceManager(requestInputStream.getBytes(Charset.defaultCharset()), new ByteArrayOutputStream());
      }
   }
}
