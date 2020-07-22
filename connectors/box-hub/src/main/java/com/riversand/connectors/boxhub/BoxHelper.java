package com.riversand.connectors.boxhub;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivateKey;
import java.security.Security;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.riversand.connectors.extension.helpers.ConnectorServiceHelper;
import com.riversand.connectors.extension.base.SyndicationSettings;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManager;
import com.riversand.dataplatform.ps.diagnosticmanager.ProfilerManagerLogger;
import com.riversand.rsconnect.common.helpers.ConnectRuntimeException;
import com.riversand.rsconnect.interfaces.models.JsonRecord;

import static com.riversand.connectors.extension.helpers.Constants.APPLICATION_JSON;
import static com.riversand.connectors.extension.helpers.Constants.AUTHORIZATION;
import static com.riversand.connectors.extension.helpers.Constants.BEARER;
import static com.riversand.connectors.extension.helpers.Constants.BOUNDARY_HEADER;
import static com.riversand.connectors.extension.helpers.Constants.Box.ACCESS_TOKEN;
import static com.riversand.connectors.extension.helpers.Constants.Box.CLAIM_NAME;
import static com.riversand.connectors.extension.helpers.Constants.Box.CLAIM_VALUE;
import static com.riversand.connectors.extension.helpers.Constants.Box.ENTRIES;
import static com.riversand.connectors.extension.helpers.Constants.Box.JWT_EXPIRATION;
import static com.riversand.connectors.extension.helpers.Constants.Box.JWT_TYP_HEADER;
import static com.riversand.connectors.extension.helpers.Constants.Box.JWT_VALUE;
import static com.riversand.connectors.extension.helpers.Constants.Box.KEY_ID_HEADER;
import static com.riversand.connectors.extension.helpers.Constants.ConnectorService.BOUNDARY;
import static com.riversand.connectors.extension.helpers.Constants.LogCodes.RSC_7273;
import static com.riversand.connectors.extension.helpers.Constants.MULTIPART_FORM_DATA;
import static com.riversand.connectors.extension.helpers.Constants.POSTREQUESTMETHOD;
import static com.riversand.connectors.extension.helpers.Constants.PropertyNames.ID;
import static com.riversand.connectors.extension.helpers.Constants.SPACE;
import static com.riversand.rsconnect.interfaces.constants.Constants.Services.RSCONNECT_SERVICE;

public class BoxHelper {
   private static ProfilerManagerLogger pmLogger = ProfilerManager.getLogger(BoxHelper.class);

   private BoxHelper() {
      throw new IllegalStateException("BoxHelper should be used as a utility class");
   }

   protected static String getAccessToken(String url, JsonObject requestBody, String tenantId) throws IOException {
      String accessToken = null;

      HttpURLConnection httpURLConnection = ConnectorServiceHelper.buildHttpURLConnection(url, POSTREQUESTMETHOD, APPLICATION_JSON, true, tenantId);
      httpURLConnection.setDoInput(true);
      JsonObject jsonResponse = ConnectorServiceHelper.sendHttpRequest(requestBody.toString(), httpURLConnection, tenantId);

      if (jsonResponse.size() > 0 && jsonResponse.has(ACCESS_TOKEN)) {
         accessToken = JsonRecord.getValue(jsonResponse, ACCESS_TOKEN);
      }

      return accessToken;
   }

   public static String sendHubRequest(String url, JsonElement requestBody, byte[] file, String requestMethod, String accessToken, StringBuilder apiError, String tenantId) throws IOException {
      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Payload for sendHubRequest: [%s] ", requestBody.toString()));
      String hubId = null;
      try {
         StringBuilder contentType = new StringBuilder();
         contentType.append(MULTIPART_FORM_DATA);
         contentType.append(BOUNDARY_HEADER);
         contentType.append(BOUNDARY);

         HttpURLConnection httpURLConnection = ConnectorServiceHelper.buildHttpURLConnection(url, requestMethod, contentType.toString(), true, tenantId);
         httpURLConnection.setRequestProperty(AUTHORIZATION, BEARER.concat(SPACE).concat(accessToken));

         JsonObject jsonResponse = ConnectorServiceHelper.sendHttpRequest(requestBody.toString(), true, file, httpURLConnection, null, tenantId);

         if (!jsonResponse.isJsonNull() && jsonResponse.size() > 0) {
            pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Response for Box req: " + jsonResponse.toString());
            JsonArray entriesArray = JsonRecord.findArray(jsonResponse, ENTRIES);
            if (entriesArray != null && entriesArray.size() > 0) {
               hubId = JsonRecord.getValue(entriesArray.get(0), ID);
            }
            pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, String.format("Box item id is: [%s]", hubId));
         }
      } catch (ConnectRuntimeException ex) {
         ConnectorServiceHelper.extractHttpErrors(apiError, ex);
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, url, ex);
      } catch (Exception ex) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, url, ex);
         throw new IOException(String.format("Box connector failed to push the message to url [%s]", url));
      }

      pmLogger.info(tenantId, RSCONNECT_SERVICE, RSC_7273, "Box Item Id: " + hubId);
      return hubId;
   }

   public static byte[] downloadBlob(String assetURL, long limit) throws IOException {
      byte[] byteArray;
      try (BufferedInputStream inputStream = new BufferedInputStream(new URL(assetURL).openStream())) {
         try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            int nRead;
            long totRead = 0;
            byte[] data = new byte[1024];
            while (totRead <= limit && (nRead = inputStream.read(data, 0, data.length)) != -1) {
               buffer.write(data, 0, nRead);
               totRead += nRead;
            }
            buffer.flush();
            byteArray = buffer.toByteArray();
         }
      }
      return byteArray;
   }

   public static String getAssertion(SyndicationSettings syndicationSettings, String tenantId) {
      try {
         PrivateKey key = getPrivateKey(syndicationSettings);

         JsonWebSignature jws = getJsonWebSignature(syndicationSettings, key);

         return jws.getCompactSerialization();
      } catch (IOException | JoseException | OperatorCreationException | PKCSException ex) {
         pmLogger.error(tenantId, RSCONNECT_SERVICE, RSC_7273, ex);
      }
      return null;
   }

   private static JsonWebSignature getJsonWebSignature(SyndicationSettings syndicationSettings, PrivateKey key) {
      JwtClaims claims = new JwtClaims();
      claims.setIssuer(syndicationSettings.getClientId());
      claims.setAudience(syndicationSettings.getAuthurl());
      claims.setSubject(syndicationSettings.getEnterpriseId());
      claims.setClaim(CLAIM_NAME, CLAIM_VALUE);
      claims.setGeneratedJwtId(64);
      claims.setExpirationTimeMinutesInTheFuture(JWT_EXPIRATION);

      JsonWebSignature jws = new JsonWebSignature();
      jws.setPayload(claims.toJson());
      jws.setKey(key);

      jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA512);
      jws.setHeader(JWT_TYP_HEADER, JWT_VALUE);
      jws.setHeader(KEY_ID_HEADER, syndicationSettings.getPublicKeyId());
      return jws;
   }

   private static PrivateKey getPrivateKey(SyndicationSettings syndicationSettings) throws IOException, OperatorCreationException, PKCSException {
      Security.addProvider(new BouncyCastleProvider());

      PEMParser pemParser = new PEMParser(new StringReader(syndicationSettings.getPrivateKey()));
      Object keyPair;
      keyPair = pemParser.readObject();

      pemParser.close();

      // Finally, we decrypt the key using the passphrase
      JceOpenSSLPKCS8DecryptorProviderBuilder decryptBuilder = new JceOpenSSLPKCS8DecryptorProviderBuilder()
            .setProvider("BC");
      InputDecryptorProvider decryptProvider;
      decryptProvider = decryptBuilder.build(syndicationSettings.getPassphrase().toCharArray());
      PrivateKeyInfo keyInfo;
      keyInfo = ((PKCS8EncryptedPrivateKeyInfo) keyPair).decryptPrivateKeyInfo(decryptProvider);

      return (new JcaPEMKeyConverter()).getPrivateKey(keyInfo);
   }

}
