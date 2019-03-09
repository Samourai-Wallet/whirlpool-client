package com.samourai.whirlpool.client.wallet.persist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java8.util.function.Function;
import java8.util.function.Predicate;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWhirlpoolUtxoConfigHandler {
  private static final Logger log = LoggerFactory.getLogger(FileWhirlpoolUtxoConfigHandler.class);
  private static final int LASTSEEN_EXPIRY = 3600; // 1 hour

  private File file;
  private final ObjectMapper mapper;
  private ConcurrentHashMap<String, WhirlpoolUtxoConfig> utxoConfigs;
  private long lastWrite;

  protected FileWhirlpoolUtxoConfigHandler(File file) {
    this.file = file;
    this.mapper = new ObjectMapper();
    this.utxoConfigs = new ConcurrentHashMap<String, WhirlpoolUtxoConfig>();
    this.lastWrite = 0;
    load();
  }

  protected WhirlpoolUtxoConfig get(String key) {
    return utxoConfigs.get(key);
  }

  protected void set(String key, WhirlpoolUtxoConfig value) {
    utxoConfigs.put(key, value);
    write();
  }

  protected void save() {
    // check for modifications
    boolean anyModified =
        StreamSupport.stream(utxoConfigs.entrySet())
            .filter(
                new Predicate<Entry<String, WhirlpoolUtxoConfig>>() {
                  @Override
                  public boolean test(Entry<String, WhirlpoolUtxoConfig> entry) {
                    return entry.getValue().getLastModified() > lastWrite;
                  }
                })
            .findAny()
            .isPresent();

    if (!anyModified) {
      if (log.isDebugEnabled()) {
        log.debug("nothing to write (no WhirlpoolUtxoConfig modified)");
      }
      return;
    }

    // save
    write();
  }

  private void load() {
    try {
      utxoConfigs.clear();
      Map<String, WhirlpoolUtxoConfig> readValue =
          mapper.readValue(file, new TypeReference<Map<String, WhirlpoolUtxoConfig>>() {});
      utxoConfigs.putAll(readValue);
    } catch (Exception e) {
      log.error("Unable to read " + file.getAbsolutePath());
    }
  }

  private void write() {
    if (log.isDebugEnabled()) {
      log.debug("write");
    }

    try {
      // remove obsoletes from map
      final long lastSeenMin = System.currentTimeMillis() - (LASTSEEN_EXPIRY * 1000);
      Iterator<Entry<String, WhirlpoolUtxoConfig>> iter = utxoConfigs.entrySet().iterator();
      while (iter.hasNext()) {
        Entry<String, WhirlpoolUtxoConfig> entry = iter.next();
        if (entry.getValue().getLastSeen() < lastSeenMin) {
          if (log.isDebugEnabled()) {
            log.debug("Cleanup obsolete entry: " + entry.getValue());
          }
          iter.remove();
        }
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
    } catch (Exception e) {
      log.error("Unable to write file " + file.getAbsolutePath());
    }
  }
}
