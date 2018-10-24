package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPremixHandler;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;

public class MixParams {
  private String poolId;
  private long denomination;
  private IPremixHandler premixHandler;
  private IPostmixHandler postmixHandler;

  public MixParams(
      String poolId,
      long denomination,
      IPremixHandler premixHandler,
      IPostmixHandler postmixHandler) {
    this.poolId = poolId;
    this.denomination = denomination;
    this.premixHandler = premixHandler;
    this.postmixHandler = postmixHandler;
  }

  public MixParams(Pool pool, IPremixHandler premixHandler, IPostmixHandler postmixHandler) {
    this(pool.getPoolId(), pool.getDenomination(), premixHandler, postmixHandler);
  }

  public MixParams(MixParams mixParams, IPremixHandler premixHandler) {
    this(
        mixParams.getPoolId(),
        mixParams.getDenomination(),
        premixHandler,
        mixParams.getPostmixHandler());
  }

  public String getPoolId() {
    return poolId;
  }

  public long getDenomination() {
    return denomination;
  }

  public IPremixHandler getPremixHandler() {
    return premixHandler;
  }

  public IPostmixHandler getPostmixHandler() {
    return postmixHandler;
  }
}
