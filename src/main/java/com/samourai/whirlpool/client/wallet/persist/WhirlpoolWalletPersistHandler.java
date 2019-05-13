package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import java.util.Collection;

public interface WhirlpoolWalletPersistHandler {

  // index

  IIndexHandler getIndexHandler(String key);

  IIndexHandler getIndexHandler(String key, int defaultValue);

  boolean isInitialized();

  void setInitialized(boolean value);

  // utxo

  void loadUtxoConfigs(WhirlpoolWallet whirlpoolWallet);

  WhirlpoolUtxoConfig getUtxoConfig(String utxoHash, int utxoIndex);

  WhirlpoolUtxoConfig getUtxoConfig(String utxoHash);

  void setUtxoConfig(String utxoHash, int utxoIndex, WhirlpoolUtxoConfig value);

  void setUtxoConfig(String utxoHash, WhirlpoolUtxoConfig value);

  void cleanUtxoConfig(Collection<WhirlpoolUtxo> knownUtxos);

  void save() throws Exception;

  void onUtxoConfigChanged(WhirlpoolUtxoConfig whirlpoolUtxoConfig);
}
