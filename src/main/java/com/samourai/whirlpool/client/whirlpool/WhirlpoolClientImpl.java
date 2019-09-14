package com.samourai.whirlpool.client.whirlpool;

import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.listener.MixClientListener;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolClientImpl implements WhirlpoolClient {
  private Logger log;

  private WhirlpoolClientConfig config;

  private boolean done;
  private String logPrefix;

  private MixClient mixClient;
  private Thread mixThread;
  private WhirlpoolClientListener listener;

  /**
   * Get a new Whirlpool client.
   *
   * @param config client configuration (server...)
   * @return
   */
  public static WhirlpoolClient newClient(WhirlpoolClientConfig config) {
    String logPrefix = Long.toString(System.currentTimeMillis());
    return new WhirlpoolClientImpl(config, logPrefix);
  }

  private WhirlpoolClientImpl(WhirlpoolClientConfig config, String logPrefix) {
    this.log = LoggerFactory.getLogger(WhirlpoolClientImpl.class + "[" + logPrefix + "]");
    this.config = config;
    this.logPrefix = logPrefix;
    if (log.isDebugEnabled()) {
      log.debug("+whirlpoolClient");
    }
    log.info("+whirlpoolClient"); // TODO !!!!
  }

  @Override
  public Pools fetchPools() throws HttpException, NotifiableException {
    String url = WhirlpoolProtocol.getUrlFetchPools(config.getServer());
    try {
      PoolsResponse poolsResponse = config.getHttpClient().getJson(url, PoolsResponse.class, null);
      return computePools(poolsResponse);
    } catch (HttpException e) {
      String restErrorResponseMessage = ClientUtils.parseRestErrorMessage(e);
      if (restErrorResponseMessage != null) {
        throw new NotifiableException(restErrorResponseMessage);
      }
      throw e;
    }
  }

  private Pools computePools(PoolsResponse poolsResponse) {
    List<Pool> listPools = new ArrayList<Pool>();
    for (PoolInfo poolInfo : poolsResponse.pools) {
      Pool pool = new Pool();
      pool.setPoolId(poolInfo.poolId);
      pool.setDenomination(poolInfo.denomination);
      pool.setFeeValue(poolInfo.feeValue);
      pool.setMustMixBalanceMin(poolInfo.mustMixBalanceMin);
      pool.setMustMixBalanceCap(poolInfo.mustMixBalanceCap);
      pool.setMustMixBalanceMax(poolInfo.mustMixBalanceMax);
      pool.setMinAnonymitySet(poolInfo.minAnonymitySet);
      pool.setMinMustMix(poolInfo.minMustMix);
      pool.setNbRegistered(poolInfo.nbRegistered);

      pool.setMixAnonymitySet(poolInfo.mixAnonymitySet);
      pool.setMixStatus(poolInfo.mixStatus);
      pool.setElapsedTime(poolInfo.elapsedTime);
      pool.setNbConfirmed(poolInfo.nbConfirmed);
      listPools.add(pool);
    }
    Pools pools = new Pools(listPools);
    return pools;
  }

  @Override
  public void whirlpool(final MixParams mixParams, WhirlpoolClientListener listener) {
    this.listener = listener;

    this.mixThread =
        new Thread(
            new Runnable() {
              @Override
              public synchronized void run() {
                runClient(mixParams);
                while (!done) {
                  try {
                    synchronized (mixThread) {
                      mixThread.wait();
                    }
                  } catch (Exception e) {
                  }
                }
              }
            },
            "whirlpoolClient-" + logPrefix);
    this.mixThread.start();
  }

  private void runClient(MixParams mixParams) {
    MixClientListener mixListener = computeMixListener();

    mixClient = new MixClient(config, logPrefix);
    mixClient.whirlpool(mixParams, mixListener);
  }

  private MixClientListener computeMixListener() {
    return new MixClientListener() {
      @Override
      public void success(MixSuccess mixSuccess) {
        // done
        listener.success(mixSuccess);
        exit();
      }

      @Override
      public void fail(MixFailReason reason, String notifiableError) {
        listener.fail(reason, notifiableError);
        exit();
      }

      @Override
      public void progress(MixStep step) {
        listener.progress(step);
      }
    };
  }

  @Override
  public void exit() {
    if (!done) {
      if (log.isDebugEnabled()) {
        log.debug("--whirlpoolClient");
      }
      done = true;
      if (mixClient != null) {
        mixClient.exit();
      }
      if (mixThread != null) {
        synchronized (mixThread) {
          mixThread.notify();
        }
      }
    }
  }

  public MixClient getMixClient() {
    return mixClient;
  }
}
