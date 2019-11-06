package com.samourai.api.client;

import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.util.oauth.OAuthManager;
import com.samourai.whirlpool.client.exception.NotifiableException;

public class WhirlpoolBackendApi extends BackendApi {

  public WhirlpoolBackendApi(IHttpClient httpClient, String urlBackend, OAuthManager oAuthManager) {
    super(httpClient, urlBackend, oAuthManager);
  }

  @Override
  public void pushTx(String txHex) throws NotifiableException {
    try {
      super.pushTx(txHex);
    } catch (Exception e) {
      // preserve pushTx message
      throw new NotifiableException(e.getMessage());
    }
  }
}
