package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.beans.Pool;
import com.samourai.whirlpool.client.beans.Pools;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixClientListener;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class WhirlpoolClientImpl implements WhirlpoolClient {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private WhirlpoolClientConfig config;
    private String poolId;
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
    public Pools fetchPools() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://" + this.config.getServer() + WhirlpoolProtocol.ENDPOINT_POOLS; // TODO HTTPS
        ResponseEntity<PoolsResponse> result = restTemplate.getForEntity(url, PoolsResponse.class);
        if (result == null || !result.getStatusCode().is2xxSuccessful()) {
            // response error
            throw new Exception("unable to retrieve pools");
        }
        return computePools(result.getBody());
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
    public void whirlpool(String poolId, MixParams mixParams, int mixs, WhirlpoolClientListener listener) {
        this.poolId = poolId;
        this.mixs = mixs;
        this.listener = listener;
        this.doneMixs = 0;
        this.mixClients = new ArrayList<>();

        new Thread(() -> {
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
        }).start();

    }

    private MixClient runClient(MixParams mixParams) {
        MixClientListener mixListener = computeMixListener();

        MixClient mixClient = new MixClient(config, poolId);
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

    private void onMixsuccess(MixSuccess mixSuccess) {
        listener.mixSuccess(doneMixs+1, mixs, mixSuccess);

        this.doneMixs++;
        if (doneMixs == mixs) {
            // all mixs done
            listener.success(doneMixs);
        }
        else {
            // go to next mix
            MixClient mixClient = getLastWhirlpoolClient();
            MixParams nextMixParams = mixClient.computeNextMixParams();
            runClient(nextMixParams);
        }
    }

    private MixClientListener computeMixListener() {
        return new MixClientListener() {
            @Override
            public void success(MixSuccess mixSuccess) {
                onMixsuccess(mixSuccess);
            }

            @Override
            public void fail() {
                listener.fail(doneMixs+1, mixs);
            }

            @Override
            public void progress(MixStatus mixStatus, int currentStep, int nbSteps) {
                listener.progress(doneMixs+1, mixs, mixStatus, currentStep, nbSteps);
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

    public void debugState() {
        if (log.isDebugEnabled()) {
            log.debug("Mix "+doneMixs+"/"+ mixs);
            for (MixClient mixClient : mixClients) {
                mixClient.debugState();
            }
        }
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public MixClient getMixClient(int mix) {
        return mixClients.get(mix-1);
    }

}
