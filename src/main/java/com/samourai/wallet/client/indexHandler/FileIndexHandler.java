package com.samourai.wallet.client.indexHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileIndexHandler {
  private static final Logger log = LoggerFactory.getLogger(FileIndexHandler.class);

  private File file;
  private ObjectMapper mapper = new ObjectMapper();
  private Map<String, Integer> indexes = new HashMap<String, Integer>();

  public FileIndexHandler(File file) {
    this.file = file;
    load();
  }

  public int get(String key, int defaultValue) {
    if (!indexes.containsKey(key)) {
      return defaultValue;
    }
    return indexes.get(key);
  }

  public int get(String key) {
    return get(key, IIndexHandler.DEFAULT_VALUE);
  }

  public synchronized int getAndIncrement(String key, int defaultValue) {
    int value = get(key, defaultValue);
    set(key, value + 1);
    return value;
  }

  public synchronized int getAndIncrement(String key) {
    return getAndIncrement(key, IIndexHandler.DEFAULT_VALUE);
  }

  public void set(String key, int value) {
    indexes.put(key, value);
    write();
  }

  public ItemFileIndexHandler getIndexHandler(String key, int defaultValue) {
    return new ItemFileIndexHandler(this, key, defaultValue);
  }

  public ItemFileIndexHandler getIndexHandler(String key) {
    return getIndexHandler(key, IIndexHandler.DEFAULT_VALUE);
  }

  private void load() {
    try {
      indexes = mapper.readValue(file, Map.class);
    } catch (Exception e) {
      log.error("Unable to read " + file.getAbsolutePath() + ", resetting indexes");
    }
  }

  private void write() {
    try {
      mapper.writeValue(file, indexes);
    } catch (Exception e) {
      log.error("Unable to write file " + file.getAbsolutePath());
    }
  }
}
