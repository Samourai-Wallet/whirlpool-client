package com.samourai.whirlpool.client.whirlpool;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.listener.MixClientListener;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.httpClient.HttpClient;
import com.samourai.whirlpool.client.whirlpool.httpClient.HttpException;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class WhirlpoolClientImpl implements WhirlpoolClient {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private WhirlpoolClientConfig config;
    private HttpClient httpClient;

    private String poolId;
    private long denomination;
    private int mixs;
    private int doneMixs;
    private String logPrefix;

    private List<MixClient> mixClients;
    private WhirlpoolClientListener listener;

    /**
     * Get a new Whirlpool client.
     * @param config client configuration (server...)
     * @return
     */
    public static WhirlpoolClient newClient(WhirlpoolClientConfig config, HttpClient httpClient) {
        return new WhirlpoolClientImpl(config, httpClient);
    }

    private WhirlpoolClientImpl(WhirlpoolClientConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.logPrefix = null;
        if (log.isDebugEnabled()) {
            log.debug("protocolVersion=" + WhirlpoolProtocol.PROTOCOL_VERSION);
        }
    }

    @Override
    public Pools fetchPools() throws HttpException, NotifiableException {
        String url = "http://" + this.config.getServer() + WhirlpoolProtocol.ENDPOINT_POOLS; // TODO HTTPS
        try {
            PoolsResponse poolsResponse = this.httpClient.getForEntity(url, PoolsResponse.class);
            return computePools(poolsResponse);
        } catch(HttpException e) {
            String restErrorResponseMessage = ClientUtils.parseRestErrorMessage(e);
            if (restErrorResponseMessage != null) {
                throw new NotifiableException(restErrorResponseMessage);
            }
            throw e;
        }
    }

    private Pools computePools(PoolsResponse poolsResponse) {
        Pools pools = new Pools();
        List<Pool> listPools = new ArrayList<>();
        for (PoolInfo poolInfo : poolsResponse.pools) {
            Pool pool = new Pool();
            pool.setPoolId(poolInfo.poolId);
            pool.setDenomination(poolInfo.denomination);
            pool.setMinerFeeMin(poolInfo.minerFeeMin);
            pool.setMinerFeeMax(poolInfo.minerFeeMax);
            pool.setMinAnonymitySet(poolInfo.minAnonymitySet);
            pool.setMixAnonymitySet(poolInfo.mixAnonymitySet);
            pool.setMixStatus(poolInfo.mixStatus);
            pool.setElapsedTime(poolInfo.elapsedTime);
            pool.setMixNbConnected(poolInfo.mixNbConnected);
            pool.setMixNbRegistered(poolInfo.mixNbRegistered);
            listPools.add(pool);
        }
        pools.setPools(listPools);
        return pools;
    }

    @Override
    public void whirlpool(String poolId, long denomination, final MixParams mixParams, int mixs, WhirlpoolClientListener listener) {
        this.poolId = poolId;
        this.denomination = denomination;
        this.mixs = mixs;
        this.listener = listener;
        this.doneMixs = 0;
        this.mixClients = new ArrayList<>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MixClient mixClient = runClient(mixParams);

                    synchronized (this) {
                        while(!mixClient.isDone()) {
                            wait(1000);
                        }
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }).start();
    }

    private MixClient runClient(MixParams mixParams) {
        MixClientListener mixListener = computeMixListener();

        MixClient mixClient = new MixClient(config, httpClient, poolId, denomination);
        if (logPrefix != null) {
            int mix = this.mixClients.size();
            mixClient.setLogPrefix(logPrefix+"["+(mix+1)+"]");
        }
        mixClient.whirlpool(mixParams, mixListener);
        this.mixClients.add(mixClient);
        return mixClient;
    }

    private MixClient getLastWhirlpoolClient() {
        return mixClients.get(mixClients.size() - 1);
    }

    private void onMixsuccess(MixSuccess mixSuccess, MixParams nextMixParams) {
        listener.mixSuccess(doneMixs+1, mixs, mixSuccess);

        this.doneMixs++;
        if (doneMixs == mixs) {
            // all mixs done
            listener.success(mixs, mixSuccess);
        }
        else {
            // go to next mix
            runClient(nextMixParams);
        }
    }

    private MixClientListener computeMixListener() {
        return new MixClientListener() {
            @Override
            public void success(MixSuccess mixSuccess, MixParams nextMixParams) {
                onMixsuccess(mixSuccess, nextMixParams);
            }

            @Override
            public void fail() {
                listener.fail(doneMixs+1, mixs);
            }

            @Override
            public void progress(MixStep step, String stepInfo, int stepNumber, int nbSteps) {
                listener.progress(doneMixs+1, mixs, step, stepInfo, stepNumber, nbSteps);
            }
        };
    }

    @Override
    public void exit() {
        MixClient mixClient = getLastWhirlpoolClient();
        if (mixClient != null) {
            mixClient.exit();
        }
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public MixClient getMixClient(int mix) {
        return mixClients.get(mix-1);
    }

}
