package com.samourai.whirlpool.client.wallet.persist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWhirlpoolUtxoConfigHandler {
  private static final Logger log = LoggerFactory.getLogger(FileWhirlpoolUtxoConfigHandler.class);

  private File file;
  private final ObjectMapper mapper;
  private ConcurrentHashMap<String, WhirlpoolUtxoConfig> utxoConfigs;
  private Set<String> keysToClean;
  private long lastSet;
  private long lastWrite;

  protected FileWhirlpoolUtxoConfigHandler(File file) {
    this.file = file;
    this.mapper = new ObjectMapper();
    this.utxoConfigs = new ConcurrentHashMap<String, WhirlpoolUtxoConfig>();
    this.keysToClean = new HashSet<String>();
    this.lastSet = 0;
    this.lastWrite = 0;
  }

  protected WhirlpoolUtxoConfig get(String key) {
    return utxoConfigs.get(key);
  }

  protected void set(String key, WhirlpoolUtxoConfig value) {
    utxoConfigs.put(key, value);
    setLastSet();
  }

  protected boolean save() throws Exception {
    if (!hasModifications()) {
      return false;
    }

    // save
    write();
    return true;
  }

  private boolean hasModifications() {
    // check for local modifications
    return (lastSet > lastWrite);
  }

  public void loadUtxoConfigs(final WhirlpoolWallet whirlpoolWallet) {
    try {
      utxoConfigs.clear();
      if (file.exists() && file.length() > 0) {
        Map<String, WhirlpoolUtxoConfigPersisted> readValue =
            mapper.readValue(
                file, new TypeReference<Map<String, WhirlpoolUtxoConfigPersisted>>() {});
        if (log.isDebugEnabled()) {
          log.debug("load: " + readValue.size() + " utxos loaded");
        }
        // convert to WhirlpoolUtxoConfig
        Map<String, WhirlpoolUtxoConfig> whirlpoolUtxoConfigs =
            StreamSupport.stream(readValue.entrySet())
                .collect(
                    Collectors.toMap(
                        new Function<Entry<String, WhirlpoolUtxoConfigPersisted>, String>() {
                          @Override
                          public String apply(Entry<String, WhirlpoolUtxoConfigPersisted> entry) {
                            return entry.getKey();
                          }
                        },
                        new Function<
                            Entry<String, WhirlpoolUtxoConfigPersisted>, WhirlpoolUtxoConfig>() {
                          @Override
                          public WhirlpoolUtxoConfig apply(
                              Entry<String, WhirlpoolUtxoConfigPersisted> entry) {
                            return entry.getValue().toUtxoConfig(whirlpoolWallet);
                          }
                        }));
        utxoConfigs.putAll(whirlpoolUtxoConfigs);
      } else {
        if (log.isDebugEnabled()) {
          log.debug("load: skipping (file empty)");
        }
      }
      lastSet = 0;
      lastWrite = 0;
    } catch (Exception e) {
      log.warn("load: unable to read " + file.getAbsolutePath(), e);
    }
  }

  protected synchronized void clean(Set<String> knownUtxosKeys) {
    // remove obsoletes from map
    Iterator<Entry<String, WhirlpoolUtxoConfig>> iter = utxoConfigs.entrySet().iterator();
    while (iter.hasNext()) {
      Entry<String, WhirlpoolUtxoConfig> entry = iter.next();
      String entryKey = entry.getKey();
      if (!knownUtxosKeys.contains(entryKey)) {
        // entry is obsolete
        if (!keysToClean.contains(entryKey)) {
          // mark entry to clean next time
          if (log.isDebugEnabled()) {
            log.debug("Mark obsolete key: " + entryKey);
          }
          keysToClean.add(entryKey);
        } else {
          // clean now
          if (log.isDebugEnabled()) {
            log.debug("Remove obsolete key: " + entryKey);
          }
          iter.remove();
          knownUtxosKeys.remove(entryKey);
          setLastSet();
        }
      }
    }
  }

  private void write() throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("write");
    }
    // convert to WhirlpoolUtxoConfigPersisted
    Map<String, WhirlpoolUtxoConfigPersisted> mapPersisted =
        StreamSupport.stream(utxoConfigs.entrySet())
            .collect(
                Collectors.toMap(
                    new Function<Entry<String, WhirlpoolUtxoConfig>, String>() {
                      @Override
                      public String apply(Entry<String, WhirlpoolUtxoConfig> entry) {
                        return entry.getKey();
                      }
                    },
                    new Function<
                        Entry<String, WhirlpoolUtxoConfig>, WhirlpoolUtxoConfigPersisted>() {
                      @Override
                      public WhirlpoolUtxoConfigPersisted apply(
                          Entry<String, WhirlpoolUtxoConfig> entry) {
                        return new WhirlpoolUtxoConfigPersisted(entry.getValue());
                      }
                    }));

    // write
    mapper.writeValue(file, mapPersisted);
    lastWrite = System.currentTimeMillis();
  }

  public void setLastSet() {
    lastSet = System.currentTimeMillis();
  }
}
