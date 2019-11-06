package com.samourai.whirlpool.client.wallet;

import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.bip47.rpc.java.SecretPointFactoryJava;
import com.samourai.wallet.bip47.rpc.secretPoint.ISecretPointFactory;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWalletConfig extends WhirlpoolClientConfig {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletConfig.class);

  private Integer maxClients;
  private int maxClientsPerPool;
  private int clientDelay;
  private String autoTx0PoolId;
  private Tx0FeeTarget autoTx0FeeTarget;
  private boolean autoMix;

  private BackendApi backendApi;
  private int tx0Delay;
  private int tx0MinConfirmations;
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
  private MinerFeeTarget feeTargetPremix;

  private ISecretPointFactory secretPointFactory;
  private Tx0Service tx0Service;

  public WhirlpoolWalletConfig(
      IHttpClient httpClient,
      IStompClientService stompClientService,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters params,
      BackendApi backendApi) {
    super(httpClient, stompClientService, persistHandler, server, params);

    // default settings
    this.maxClients = null;
    this.maxClientsPerPool = 1;
    this.clientDelay = 30;
    this.autoTx0PoolId = null;
    this.autoTx0FeeTarget = Tx0FeeTarget.BLOCKS_4;
    this.autoMix = false;

    // technical settings
    this.backendApi = backendApi;
    this.tx0Delay = 30;
    this.tx0MinConfirmations = 1;
    this.tx0MaxOutputs = null; // spend whole utxo when possible
    this.refreshUtxoDelay = 60; // 1min
    this.refreshFeeDelay = 300; // 5min
    this.refreshPoolsDelay = 300; // 5min
    this.mixsTarget = 1;
    this.persistDelay = 4; // 4s
    this.persistCleanDelay = 300; // 5min

    this.feeMin = 1;
    this.feeMax = 510;
    this.feeFallback = 75;
    this.feeTargetPremix = MinerFeeTarget.BLOCKS_12;

    this.secretPointFactory = SecretPointFactoryJava.getInstance();
    this.tx0Service = new Tx0Service(this);
  }

  public Integer getMaxClients() {
    return maxClients;
  }

  public void setMaxClients(Integer maxClients) {
    this.maxClients = maxClients;
  }

  public int getMaxClientsPerPool() {
    return maxClientsPerPool;
  }

  public void setMaxClientsPerPool(int maxClientsPerPool) {
    this.maxClientsPerPool = maxClientsPerPool;
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

  public BackendApi getBackendApi() {
    return backendApi;
  }

  public int getTx0Delay() {
    return tx0Delay;
  }

  public void setTx0Delay(int tx0Delay) {
    this.tx0Delay = tx0Delay;
  }

  public int getTx0MinConfirmations() {
    return tx0MinConfirmations;
  }

  public void setTx0MinConfirmations(int tx0MinConfirmations) {
    this.tx0MinConfirmations = tx0MinConfirmations;
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

  public MinerFeeTarget getFeeTargetPremix() {
    return feeTargetPremix;
  }

  public void setFeeTargetPremix(MinerFeeTarget feeTargetPremix) {
    this.feeTargetPremix = feeTargetPremix;
  }

  public ISecretPointFactory getSecretPointFactory() {
    return secretPointFactory;
  }

  public void setSecretPointFactory(ISecretPointFactory secretPointFactory) {
    this.secretPointFactory = secretPointFactory;
  }

  public Tx0Service getTx0Service() {
    return tx0Service;
  }

  public void setTx0Service(Tx0Service tx0Service) {
    this.tx0Service = tx0Service;
  }

  public Map<String, String> getConfigInfo() {
    Map<String, String> configInfo = new LinkedHashMap<String, String>();
    configInfo.put(
        "server",
        "url=" + getServer() + ", network=" + getNetworkParameters().getPaymentProtocolId());
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
            + (getMaxClients() != null ? getMaxClients() : "null")
            + ", maxClientsPerPool="
            + getMaxClientsPerPool()
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
