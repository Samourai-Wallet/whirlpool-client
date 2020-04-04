package com.samourai.whirlpool.client.wallet;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.orchestrator.MixOrchestrator;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.util.ArrayList;
import java.util.Collection;
import java8.util.function.Predicate;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixOrchestratorImpl extends MixOrchestrator {
  private final Logger log = LoggerFactory.getLogger(MixOrchestratorImpl.class);

  private WhirlpoolWallet whirlpoolWallet;

  public MixOrchestratorImpl(
      MixingStateEditable mixingState, int loopDelay, WhirlpoolWallet whirlpoolWallet) {
    super(
        loopDelay,
        whirlpoolWallet.getConfig().getClientDelay(),
        computeData(mixingState, whirlpoolWallet),
        whirlpoolWallet.getConfig().getMaxClients(),
        whirlpoolWallet.getConfig().getMaxClientsPerPool(),
        whirlpoolWallet.getConfig().isAutoMix(),
        whirlpoolWallet.getConfig().getMixsTarget());
    this.whirlpoolWallet = whirlpoolWallet;
  }

  private static MixOrchestratorData computeData(
      MixingStateEditable mixingState, final WhirlpoolWallet whirlpoolWallet) {
    return new MixOrchestratorData(mixingState) {
      @Override
      public Stream<WhirlpoolUtxo> getQueue() {
        try {
          return StreamSupport.stream(
                  whirlpoolWallet.getUtxos(
                      false, WhirlpoolAccount.PREMIX, WhirlpoolAccount.POSTMIX))
              .filter(
                  new Predicate<WhirlpoolUtxo>() {
                    @Override
                    public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                      // queued
                      return WhirlpoolUtxoStatus.MIX_QUEUE.equals(
                          whirlpoolUtxo.getUtxoState().getStatus());
                    }
                  });
        } catch (Exception e) {
          return StreamSupport.stream(new ArrayList());
        }
      }

      @Override
      public Collection<Pool> getPools() throws Exception {
        return whirlpoolWallet.getPools();
      }
    };
  }

  @Override
  protected void onMixSuccess(WhirlpoolUtxo whirlpoolUtxo, MixSuccess mixSuccess) {
    super.onMixSuccess(whirlpoolUtxo, mixSuccess);
    whirlpoolWallet.onMixSuccess(whirlpoolUtxo, mixSuccess);
  }

  @Override
  protected void onMixFail(
      WhirlpoolUtxo whirlpoolUtxo, MixFailReason reason, String notifiableError) {
    super.onMixFail(whirlpoolUtxo, reason, notifiableError);
    whirlpoolWallet.onMixFail(whirlpoolUtxo, reason, notifiableError);
  }

  @Override
  protected WhirlpoolClient runWhirlpoolClient(
      WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener listener) throws NotifiableException {
    if (log.isDebugEnabled()) {
      log.info(
          " • Connecting client to pool: "
              + whirlpoolUtxo.getUtxoConfig().getPoolId()
              + ", utxo="
              + whirlpoolUtxo
              + " ; "
              + whirlpoolUtxo.getUtxoConfig());
    } else {
      log.info(" • Connecting client to pool: " + whirlpoolUtxo.getUtxoConfig().getPoolId());
    }

    // find pool
    String poolId = whirlpoolUtxo.getUtxoConfig().getPoolId();
    Pool pool = null;
    try {
      pool = whirlpoolWallet.findPoolById(poolId);
    } catch (Exception e) {
      log.error("", e);
    }
    if (pool == null) {
      throw new NotifiableException("Pool not found: " + poolId);
    }

    // start mixing (whirlpoolClient will start a new thread)
    MixParams mixParams = computeMixParams(whirlpoolUtxo, pool);
    WhirlpoolClient whirlpoolClient = whirlpoolWallet.getConfig().newClient();
    whirlpoolClient.whirlpool(mixParams, listener);
    return whirlpoolClient;
  }

  private MixParams computeMixParams(WhirlpoolUtxo whirlpoolUtxo, Pool pool) {
    IPremixHandler premixHandler = computePremixHandler(whirlpoolUtxo);
    IPostmixHandler postmixHandler = computePostmixHandler();
    return new MixParams(pool.getPoolId(), pool.getDenomination(), premixHandler, postmixHandler);
  }

  @Override
  protected void stopWhirlpoolClient(
      final Mixing mixing, final boolean cancel, final boolean reQueue) {
    super.stopWhirlpoolClient(mixing, cancel, reQueue);

    // stop in new thread for faster response
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                mixing.getWhirlpoolClient().stop(cancel);

                if (reQueue) {
                  try {
                    mixQueue(mixing.getUtxo());
                  } catch (Exception e) {
                    log.error("", e);
                  }
                }
              }
            },
            "stop-whirlpoolClient")
        .start();
  }

  private IPremixHandler computePremixHandler(WhirlpoolUtxo whirlpoolUtxo) {
    HD_Address premixAddress =
        whirlpoolWallet.getWallet(whirlpoolUtxo.getAccount()).getAddressAt(whirlpoolUtxo.getUtxo());
    ECKey premixKey = premixAddress.getECKey();

    UnspentResponse.UnspentOutput premixOrPostmixUtxo = whirlpoolUtxo.getUtxo();
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(
            premixOrPostmixUtxo.tx_hash,
            premixOrPostmixUtxo.tx_output_n,
            premixOrPostmixUtxo.value);

    // use PREMIX(0,0) as userPreHash (not transmitted to server but rehashed with another salt)
    HD_Address premix00 = whirlpoolWallet.getWallet(WhirlpoolAccount.PREMIX).getAddressAt(0, 0);
    NetworkParameters params = whirlpoolWallet.getConfig().getNetworkParameters();
    String premix00Bech32 = Bech32UtilGeneric.getInstance().toBech32(premix00, params);
    String userPreHash = ClientUtils.sha256Hash(premix00Bech32);

    return new PremixHandler(utxoWithBalance, premixKey, userPreHash);
  }

  private IPostmixHandler computePostmixHandler() {
    return new Bip84PostmixHandler(
        whirlpoolWallet.getWalletPostmix(), whirlpoolWallet.getConfig().isMobile());
  }
}
