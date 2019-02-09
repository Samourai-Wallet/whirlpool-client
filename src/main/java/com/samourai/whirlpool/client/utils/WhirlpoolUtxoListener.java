package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolUtxoListener {
  private static final Logger log = LoggerFactory.getLogger(WhirlpoolUtxoListener.class);
  private static final int SLEEP_TIME = 2000;

  protected List<WhirlpoolUtxo> whirlpoolUtxos;

  public WhirlpoolUtxoListener() {
    this.whirlpoolUtxos = new ArrayList<WhirlpoolUtxo>();
  }

  public synchronized void register(WhirlpoolUtxo whirlpoolUtxo) {
    this.whirlpoolUtxos.add(whirlpoolUtxo);
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone(int nbSuccessExpected) {
    do {
      int nbSuccess = 0;
      for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
        switch (whirlpoolUtxo.getStatus()) {
          case MIX_SUCCESS:
            nbSuccess++;
            break;
          case MIX_FAILED:
            return false;
        }
      }
      if (nbSuccess >= nbSuccessExpected) {
        return true;
      }

      // will be notified by listeners to wakeup
      try {
        if (log.isDebugEnabled()) {
          log.debug(whirlpoolUtxos.size() + " mixs in progress..");
          int i = 0;
          for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
            log.debug("#" + i + ": " + whirlpoolUtxo);
            i++;
          }
        }
        Thread.sleep(SLEEP_TIME);
      } catch (InterruptedException e) {
      }
    } while (true);
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone() {
    return waitDone(whirlpoolUtxos.size());
  }
}
