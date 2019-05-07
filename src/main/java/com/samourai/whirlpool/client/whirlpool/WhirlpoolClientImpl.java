package com.samourai.whirlpool.client.whirlpool;

import com.samourai.http.client.HttpException;
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
  private Logger log = LoggerFactory.getLogger(WhirlpoolClientImpl.class);

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
    return new WhirlpoolClientImpl(config);
  }

  private WhirlpoolClientImpl(WhirlpoolClientConfig config) {
    this.config = config;
    this.logPrefix = null;
    if (log.isDebugEnabled()) {
      log.debug("protocolVersion=" + WhirlpoolProtocol.PROTOCOL_VERSION);
    }
  }

  @Override
  public Pools fetchPools() throws HttpException, NotifiableException {
    String url =
        WhirlpoolProtocol.getUrlFetchPools(config.getServer(), config.isSsl(), config.getScode());
    try {
      PoolsResponse poolsResponse = config.getHttpClient().parseJson(url, PoolsResponse.class);
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
    Pools pools =
        new Pools(
            listPools,
            poolsResponse.feePaymentCode,
            WhirlpoolProtocol.decodeBytes(poolsResponse.feePayload64));
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
                    wait();
                  } catch (Exception e) {
                  }
                }
              }
            });
    this.mixThread.start();
  }

  private void runClient(MixParams mixParams) {
    MixClientListener mixListener = computeMixListener();

    mixClient = new MixClient(config);
    if (logPrefix != null) {
      mixClient.setLogPrefix(logPrefix);
    }
    mixClient.whirlpool(mixParams, mixListener);
  }

  private MixClientListener computeMixListener() {
    return new MixClientListener() {
      @Override
      public void success(MixSuccess mixSuccess, MixParams nextMixParams) {
        // done
        listener.success(mixSuccess);
        endMixThread();
      }

      @Override
      public void fail(MixFailReason reason, String notifiableError) {
        listener.fail(reason, notifiableError);
        endMixThread();
      }

      @Override
      public void progress(MixStep step) {
        listener.progress(step);
      }
    };
  }

  private void endMixThread() {
    synchronized (mixThread) {
      done = true;
      mixThread.notify();
    }
  }

  @Override
  public void exit() {
    if (mixClient != null) {
      mixClient.exit();
    }
  }

  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }

  public MixClient getMixClient() {
    return mixClient;
  }
}
