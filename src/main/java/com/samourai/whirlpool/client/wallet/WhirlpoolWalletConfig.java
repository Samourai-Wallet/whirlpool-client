package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClient;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.SamouraiFeeTarget;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWalletConfig extends WhirlpoolClientConfig {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletConfig.class);

  private int maxClients;
  private int clientDelay;
  private String autoTx0PoolId;
  private Tx0FeeTarget autoTx0FeeTarget;
  private boolean autoMix;

  private SamouraiApi samouraiApi;
  private PushTxService pushTxService;
  private int tx0Delay;
  private Integer tx0MaxOutputs;
  private int refreshUtxoDelay;
  private int refreshFeeDelay;
  private int refreshPoolsDelay;
  private int mixsTarget;
  private int persistDelay;
  private int persistCleanDelay;

  private int feeMin;
  private int feeMax;
  private int feeFallback;
  private SamouraiFeeTarget feeTargetPremix;

  public WhirlpoolWalletConfig(
      IHttpClient httpClient,
      IStompClient stompClient,
      WhirlpoolWalletPersistHandler persistHandler,
      String serverUrl,
      WhirlpoolServer whirlpoolServer) {
    this(
        httpClient,
        stompClient,
        persistHandler,
        serverUrl,
        whirlpoolServer.getParams(),
        whirlpoolServer.isSsl());
  }

  public WhirlpoolWalletConfig(
      IHttpClient httpClient,
      IStompClient stompClient,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters params,
      boolean ssl) {
    super(httpClient, stompClient, persistHandler, server, params, ssl);

    // default settings
    this.maxClients = 1;
    this.clientDelay = 30;
    this.autoTx0PoolId = null;
    this.autoTx0FeeTarget = Tx0FeeTarget.DEFAULT;
    this.autoMix = false;

    // technical settings
    BackendServer backendServer =
        (params instanceof MainNetParams ? BackendServer.MAINNET : BackendServer.TESTNET);
    this.samouraiApi = new SamouraiApi(httpClient, backendServer);
    this.pushTxService = samouraiApi; // use backend as default push service
    this.tx0Delay = 30;
    this.tx0MaxOutputs = null; // spend whole utxo when possible
    this.refreshUtxoDelay = 60; // 1min
    this.refreshFeeDelay = 300; // 5min
    this.refreshPoolsDelay = 300; // 5min
    this.mixsTarget = 1;
    this.persistDelay = 2; // 2s
    this.persistCleanDelay = 300; // 5min

    this.feeMin = 1;
    this.feeMax = 510;
    this.feeFallback = 75;
    this.feeTargetPremix = SamouraiFeeTarget.BLOCKS_12;
  }

  public int getMaxClients() {
    return maxClients;
  }

  public void setMaxClients(int maxClients) {
    this.maxClients = maxClients;
  }

  public int getClientDelay() {
    return clientDelay;
  }

  public void setClientDelay(int clientDelay) {
    this.clientDelay = clientDelay;
  }

  public boolean isAutoTx0() {
    return !StringUtils.isEmpty(autoTx0PoolId);
  }

  public String getAutoTx0PoolId() {
    return autoTx0PoolId;
  }

  public void setAutoTx0PoolId(String autoTx0PoolId) {
    this.autoTx0PoolId = autoTx0PoolId;
  }

  public Tx0FeeTarget getAutoTx0FeeTarget() {
    return autoTx0FeeTarget;
  }

  public void setAutoTx0FeeTarget(Tx0FeeTarget autoTx0FeeTarget) {
    this.autoTx0FeeTarget = autoTx0FeeTarget;
  }

  public boolean isAutoMix() {
    return autoMix;
  }

  public void setAutoMix(boolean autoMix) {
    this.autoMix = autoMix;
  }

  public PushTxService getPushTxService() {
    return pushTxService;
  }

  public void setPushTxService(PushTxService pushTxService) {
    this.pushTxService = pushTxService;
  }

  public SamouraiApi getSamouraiApi() {
    return samouraiApi;
  }

  public int getTx0Delay() {
    return tx0Delay;
  }

  public void setTx0Delay(int tx0Delay) {
    this.tx0Delay = tx0Delay;
  }

  public Integer getTx0MaxOutputs() {
    return tx0MaxOutputs;
  }

  public void setTx0MaxOutputs(Integer tx0MaxOutputs) {
    this.tx0MaxOutputs = tx0MaxOutputs;
  }

  public int getRefreshUtxoDelay() {
    return refreshUtxoDelay;
  }

  public void setRefreshUtxoDelay(int refreshUtxoDelay) {
    this.refreshUtxoDelay = refreshUtxoDelay;
  }

  public int getRefreshFeeDelay() {
    return refreshFeeDelay;
  }

  public void setRefreshFeeDelay(int refreshFeeDelay) {
    this.refreshFeeDelay = refreshFeeDelay;
  }

  public int getRefreshPoolsDelay() {
    return refreshPoolsDelay;
  }

  public void setRefreshPoolsDelay(int refreshPoolsDelay) {
    this.refreshPoolsDelay = refreshPoolsDelay;
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
  }

  public int getPersistDelay() {
    return persistDelay;
  }

  public void setPersistDelay(int persistDelay) {
    this.persistDelay = persistDelay;
  }

  public int getPersistCleanDelay() {
    return persistCleanDelay;
  }

  public void setPersistCleanDelay(int persistCleanDelay) {
    this.persistCleanDelay = persistCleanDelay;
  }

  public int getFeeMin() {
    return feeMin;
  }

  public void setFeeMin(int feeMin) {
    this.feeMin = feeMin;
  }

  public int getFeeMax() {
    return feeMax;
  }

  public void setFeeMax(int feeMax) {
    this.feeMax = feeMax;
  }

  public int getFeeFallback() {
    return feeFallback;
  }

  public void setFeeFallback(int feeFallback) {
    this.feeFallback = feeFallback;
  }

  public SamouraiFeeTarget getFeeTargetPremix() {
    return feeTargetPremix;
  }

  public void setFeeTargetPremix(SamouraiFeeTarget feeTargetPremix) {
    this.feeTargetPremix = feeTargetPremix;
  }

  public Map<String, String> getConfigInfo() {
    Map<String, String> configInfo = new LinkedHashMap<String, String>();
    configInfo.put(
        "server",
        "url="
            + getServer()
            + ", network="
            + getNetworkParameters().getPaymentProtocolId()
            + ", ssl="
            + Boolean.toString(isSsl()));
    configInfo.put("pushtx", getPushTxService().getClass().getName());
    configInfo.put(
        "persist",
        "persistDelay="
            + Integer.toString(getPersistDelay())
            + ", persistCleanDelay="
            + Integer.toString(getPersistCleanDelay()));
    configInfo.put(
        "refreshDelay",
        "refreshUtxoDelay="
            + refreshUtxoDelay
            + ", refreshFeeDelay"
            + refreshFeeDelay
            + ", refreshPoolsDelay="
            + refreshPoolsDelay);
    configInfo.put(
        "mix",
        "maxClients="
            + getMaxClients()
            + ", clientDelay="
            + getClientDelay()
            + ", tx0Delay="
            + getTx0Delay()
            + ", tx0MaxOutputs="
            + getTx0MaxOutputs()
            + ", autoTx0="
            + (isAutoTx0() ? getAutoTx0PoolId() : "false")
            + ", autoTx0FeeTarget="
            + getAutoTx0FeeTarget().name()
            + ", autoMix="
            + isAutoMix()
            + ", mixsTarget="
            + getMixsTarget());
    configInfo.put(
        "fee",
        "fallback="
            + getFeeFallback()
            + ", min="
            + getFeeMin()
            + ", max="
            + getFeeMax()
            + ", targetPremix="
            + getFeeTargetPremix());
    return configInfo;
  }
}
