package com.samourai.api.client;

import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SamouraiApi extends BackendApi implements PushTxService {
  private Logger log = LoggerFactory.getLogger(SamouraiApi.class);

  public SamouraiApi(IHttpClient httpClient, BackendServer backendServer) {
    super(httpClient, backendServer);
  }

  @Override
  public void pushTx(String txHex) throws NotifiableException {
    try {
      super.pushTx(txHex);
    } catch (Exception e) {
      throw new NotifiableException(e.getMessage());
    }
  }

  @Override
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
