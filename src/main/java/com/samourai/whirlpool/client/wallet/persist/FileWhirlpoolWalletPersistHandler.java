package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.indexHandler.FileIndexHandler;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWhirlpoolWalletPersistHandler implements WhirlpoolWalletPersistHandler {
  private static final Logger log =
      LoggerFactory.getLogger(FileWhirlpoolWalletPersistHandler.class);
  private static final String INDEX_INITIALIZED = "init";

  private FileIndexHandler fileIndexHandler;
  private FileWhirlpoolUtxoConfigHandler fileUtxoConfigHandler;

  public FileWhirlpoolWalletPersistHandler(File fileIndex, File fileUtxo) {
    this.fileIndexHandler = new FileIndexHandler(fileIndex);
    this.fileUtxoConfigHandler = new FileWhirlpoolUtxoConfigHandler(fileUtxo);
  }

  // --- IndexHandler

  @Override
  public IIndexHandler getIndexHandler(String key) {
    return getIndexHandler(key, IIndexHandler.DEFAULT_VALUE);
  }

  @Override
  public IIndexHandler getIndexHandler(String key, int defaultValue) {
    return fileIndexHandler.getIndexHandler(key, defaultValue);
  }

  @Override
  public boolean isInitialized() {
    return fileIndexHandler.get(INDEX_INITIALIZED, 0) == 1;
  }

  @Override
  public void setInitialized(boolean value) {
    fileIndexHandler.set(INDEX_INITIALIZED, 1);
  }

  // --- UtxoConfig

  @Override
  public void loadUtxoConfigs(WhirlpoolWallet whirlpoolWallet) {
    fileUtxoConfigHandler.loadUtxoConfigs(whirlpoolWallet);
  }

  @Override
  public WhirlpoolUtxoConfig getUtxoConfig(String utxoHash, int utxoIndex) {
    String persistKey = computeUtxoConfigKey(utxoHash, utxoIndex);
    return fileUtxoConfigHandler.get(persistKey);
  }

  @Override
  public WhirlpoolUtxoConfig getUtxoConfig(String utxoHash) {
    String persistKey = computeUtxoConfigKey(utxoHash);
    return fileUtxoConfigHandler.get(persistKey);
  }

  @Override
  public void setUtxoConfig(String utxoHash, int utxoIndex, WhirlpoolUtxoConfig value) {
    String persistKey = computeUtxoConfigKey(utxoHash, utxoIndex);
    fileUtxoConfigHandler.set(persistKey, value);
  }

  @Override
  public void setUtxoConfig(String utxoHash, WhirlpoolUtxoConfig value) {
    String persistKey = computeUtxoConfigKey(utxoHash);
    fileUtxoConfigHandler.set(persistKey, value);
  }

  @Override
  public void cleanUtxoConfig(Collection<WhirlpoolUtxo> knownUtxos) {
    Set<String> knownUtxoKeys = new HashSet<String>();
    for (WhirlpoolUtxo whirlpoolUtxo : knownUtxos) {
      UnspentOutput utxo = whirlpoolUtxo.getUtxo();
      knownUtxoKeys.add(computeUtxoConfigKey(utxo.tx_hash, utxo.tx_output_n));
    }
    fileUtxoConfigHandler.clean(knownUtxoKeys);
  }

  @Override
  public void save() throws Exception {
    fileUtxoConfigHandler.save();
  }

  @Override
  public void onUtxoConfigChanged(WhirlpoolUtxoConfig whirlpoolUtxoConfig) {
    fileUtxoConfigHandler.setLastSet();
  }

  private String computeUtxoConfigKey(String utxoHash, int utxoIndex) {
    return ClientUtils.sha256Hash(ClientUtils.utxoToKey(utxoHash, utxoIndex));
  }

  private String computeUtxoConfigKey(String utxoHash) {
    return ClientUtils.sha256Hash(utxoHash);
  }

  protected FileWhirlpoolUtxoConfigHandler getUtxoConfigHandler() {
    return fileUtxoConfigHandler;
  }
}
