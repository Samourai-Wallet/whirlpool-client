package com.samourai.whirlpool.client.utils;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.glassfish.tyrus.client.SslContextConfigurator;

public final class SSLUtil {

  public void getSslConfiguration() {
    String keyStorePath = getClass().getResource("/myapp.keystore").getPath();
    System.getProperties().put("javax.net.debug", "all"); // debug your certificate checking
    System.getProperties().put(SslContextConfigurator.KEY_STORE_FILE, keyStorePath);
    System.getProperties().put(SslContextConfigurator.TRUST_STORE_FILE, keyStorePath);
    System.getProperties().put(SslContextConfigurator.KEY_STORE_PASSWORD, "secret");
    System.getProperties().put(SslContextConfigurator.TRUST_STORE_PASSWORD, "secret");
    final SslContextConfigurator defaultConfig = new SslContextConfigurator();
    defaultConfig.retrieve(System.getProperties());
  }

  private static final TrustManager[] UNQUESTIONING_TRUST_MANAGER =
      new TrustManager[] {
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
          }

          public void checkClientTrusted(X509Certificate[] certs, String authType) {}

          public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        }
      };

  public static void disableSslChecking() throws NoSuchAlgorithmException, KeyManagementException {
    // Install the all-trusting trust manager
    final SSLContext sc = SSLContext.getInstance("SSL");
    sc.init(null, UNQUESTIONING_TRUST_MANAGER, null);
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
  }

  public static void enableSslChecking() throws KeyManagementException, NoSuchAlgorithmException {
    // Return it to the initial state (discovered by reflection, now hardcoded)
    SSLContext.getInstance("SSL").init(null, null, null);
  }

  private SSLUtil() {
    throw new UnsupportedOperationException("Do not instantiate libraries.");
  }

  public static void nuke() {
    try {
      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] myTrustedAnchors = new X509Certificate[0];
                return myTrustedAnchors;
              }

              @Override
              public void checkClientTrusted(X509Certificate[] certs, String authType) {}

              @Override
              public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
          };

      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier(
          new HostnameVerifier() {
            @Override
            public boolean verify(String arg0, SSLSession arg1) {
              return true;
            }
          });
    } catch (Exception e) {
    }
  }
}
