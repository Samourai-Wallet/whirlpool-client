package com.samourai.xmanager.client;

import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.xmanager.protocol.XManagerEnv;
import com.samourai.xmanager.protocol.XManagerProtocol;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.*;
import io.reactivex.Observable;
import java8.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XManagerClient {
  private static final Logger log = LoggerFactory.getLogger(XManagerClient.class);
  private static final XManagerProtocol protocol = XManagerProtocol.getInstance();
  private static final FormatsUtilGeneric formatUtils = FormatsUtilGeneric.getInstance();

  private String serverUrl;
  private boolean testnet;
  private IHttpClient httpClient;

  public XManagerClient(boolean testnet, boolean onion, IHttpClient httpClient) {
    this.serverUrl = XManagerEnv.get(testnet).getUrl(onion);
    this.testnet = testnet;
    this.httpClient = httpClient;
  }

  public Observable<Optional<AddressResponse>> getAddressResponse(XManagerService service) {
    String url = protocol.getUrlAddress(serverUrl);
    AddressRequest request = new AddressRequest(service.name());
    return httpClient.postJson(url, AddressResponse.class, null, request);
  }

  public String getAddressOrDefault(XManagerService service) {
    String address = null;
    try {
      Observable<Optional<AddressResponse>> responseObservable = getAddressResponse(service);
      address = responseObservable.blockingSingle().get().address;
    } catch (Exception e) {
      log.error("getAddressResponse(" + service.name() + ") failed", e);
    }
    if (address == null || !formatUtils.isValidBech32(address)) {
      log.error(
          "getAddressResponse("
              + service.name()
              + "): invalid response (address="
              + (address != null ? address : "null")
              + ") => using default address");
      address = service.getDefaultAddress(testnet);
    }
    return address;
  }

  public Observable<Optional<AddressIndexResponse>> getAddressIndexResponse(
      XManagerService service) {
    String url = protocol.getUrlAddressIndex(serverUrl);
    AddressIndexRequest request = new AddressIndexRequest(service.name());
    return httpClient.postJson(url, AddressIndexResponse.class, null, request);
  }

  public AddressIndexResponse getAddressIndexOrDefault(XManagerService service) {
    AddressIndexResponse response = null;
    try {
      Observable<Optional<AddressIndexResponse>> responseObservable =
          getAddressIndexResponse(service);
      response = responseObservable.blockingSingle().get();
    } catch (Exception e) {
      log.error("getAddressIndexResponse(" + service.name() + ") failed", e);
    }
    if (response == null || !formatUtils.isValidBech32(response.address) || response.index < 0) {
      String addressStr = response != null && response.address != null ? response.address : "null";
      String indexStr = response != null ? Integer.toString(response.index) : "null";
      log.error(
          "getAddressIndexResponse("
              + service.name()
              + "): invalid response (address="
              + addressStr
              + " index="
              + indexStr
              + ") => using default address");
      String defaultAaddress = service.getDefaultAddress(testnet);
      response = new AddressIndexResponse(defaultAaddress, 0);
    }
    return response;
  }

  public Observable<Optional<VerifyAddressIndexResponse>> verifyAddressIndexResponse(
      XManagerService service, String address, int index) {
    String url = protocol.getUrlVerifyAddressIndex(serverUrl);
    VerifyAddressIndexRequest request =
        new VerifyAddressIndexRequest(service.name(), address, index);
    return httpClient.postJson(url, VerifyAddressIndexResponse.class, null, request);
  }

  public boolean verifyAddressIndexResponseOrException(
      XManagerService service, String address, int index) throws Exception {
    try {
      Observable<Optional<VerifyAddressIndexResponse>> responseObservable =
          verifyAddressIndexResponse(service, address, index);
      return responseObservable.blockingSingle().get().valid;
    } catch (Exception e) {
      log.error("verifyAddressIndexResponse(" + service.name() + ") failed", e);
      throw e;
    }
  }
}
