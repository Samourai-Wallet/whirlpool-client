package com.samourai.api.client;

import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SamouraiApi extends BackendApi {
  private Logger log = LoggerFactory.getLogger(SamouraiApi.class);

  public SamouraiApi(IHttpClient httpClient, String urlBackend, String apiKey) {
    super(httpClient, urlBackend, apiKey);
    if (log.isDebugEnabled()) {
      String maskedApiKeyStr = apiKey != null ? ClientUtils.maskString(apiKey) : "null";
      log.debug("urlBackend=" + urlBackend + ", apiKey=" + maskedApiKeyStr);
    }
  }

  @Override
  public void pushTx(String txHex) throws NotifiableException {
    try {
      super.pushTx(txHex);
    } catch (Exception e) {
      throw new NotifiableException(e.getMessage());
    }
  }

  public boolean testConnectivity() {
    try {
      fetchFees();
      return true;
    } catch (Exception e) {
      log.error("", e);
      return false;
    }
  }
}
