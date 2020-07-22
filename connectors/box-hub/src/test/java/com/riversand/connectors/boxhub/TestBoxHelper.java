package com.riversand.connectors.boxhub;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.common.Strings;
import org.junit.Test;

import com.google.gson.JsonObject;

import com.riversand.connectors.extension.base.SyndicationSettings;
import com.riversand.rsconnect.common.helpers.GsonBuilder;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.Box.ENTERPRISE_ID;
import static com.riversand.connectors.extension.helpers.Constants.Box.PASSPHRASE_KEY;
import static com.riversand.connectors.extension.helpers.Constants.Box.PRIVATE_KEY;
import static com.riversand.connectors.extension.helpers.Constants.Box.PUBLIC_KEY_ID;
import static com.riversand.connectors.extension.helpers.Constants.HubConstants.AUTH_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class TestBoxHelper {
   String input;
   String inputResource;

   @Test
   public void testGetAssertion() throws IOException {
      inputResource = "TestKey.json";
      initialize();

      JsonObject inputJson = GsonBuilder.getGsonInstance().fromJson(input, JsonObject.class);
      SyndicationSettings syndicationSettings = new SyndicationSettings();
      syndicationSettings.setAuthurl(JsonRecord.getValue(inputJson, AUTH_URL));
      syndicationSettings.setPrivateKey(JsonRecord.getValue(inputJson, PRIVATE_KEY));
      syndicationSettings.setPassphrase(JsonRecord.getValue(inputJson, PASSPHRASE_KEY));
      syndicationSettings.setPublicKeyId(JsonRecord.getValue(inputJson, PUBLIC_KEY_ID));
      syndicationSettings.setEnterpriseId(JsonRecord.getValue(inputJson, ENTERPRISE_ID));

      inputResource = "Assertion.txt";
      initialize();
      String expectedAssertion = input.split("\\.")[0];
      String actualAssertion = BoxHelper.getAssertion(syndicationSettings, "").split("\\.")[0];
      assertNotNull(actualAssertion);
      assertEquals(expectedAssertion, actualAssertion);
      assertNotEquals("INVALID", actualAssertion);
   }

   @Test
   public void testDownloadBlob() throws IOException {
      String absolutePath = new File("").getAbsolutePath();

      StringBuilder filePath = new StringBuilder();
      filePath.append("file://");
      filePath.append(absolutePath);
      filePath.append("/src/test/resources/com/riversand/connectors/boxhub/Assertion.txt");

      byte[] downloadBlob = BoxHelper.downloadBlob(filePath.toString(), 100000);

      assertNotNull(downloadBlob);
      assertEquals(685, downloadBlob.length);
   }

   private void initialize() throws IOException {
      if (!Strings.isNullOrEmpty(inputResource)) {
         InputStream inputStream = this.getClass().getResourceAsStream(inputResource);
         input = IOUtils.toString(inputStream, Charset.defaultCharset());
      }
   }
}
