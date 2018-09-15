# whirlpool-client integration

## Dependency
Add maven/gradle dependency: com.samouraiwallet:whirlpool-client:VERSION

This will fetch the following dependencies:
 * whirlpool-protocol
 * ext-lib-j
 * bitcoinj


## Instanciate client
```
// client configuration (server...)
String server = "hostname:port";
NetworkParameters networkParameters = TestNet3Params.get();
WhirlpoolClientConfig config = new WhirlpoolClientConfig(server, networkParameters);

// instanciate client
WhirlpoolClient whirlpoolClient = WhirlpoolClientImpl.newClient(config);
```


## Fetch pools
```
// fetch pools from server with simple REST request (no websocket here)
Pools pools = whirlpoolClient.fetchPools();

// read server response
for (PoolInfo poolInfo: pools.getPools()) {
    System.out.println(poolInfo.getPoolId());
    System.out.println(poolInfo.getDenomination());
    // more info in 'poolInfo' object...
}
```

## Start mixing

```
// mix handler
ECKey ecKey = ...
BIP47Wallet bip47w = ...
int paymentCodeIndex = ...
IMixHandler mixHandler = new MixHandler(ecKey, bip47w, paymentCodeIndex);

// mix params (input, output)
String utxoHash = ...
Long utxoIndex = ...
long utxoBalance = ...
MixParams mixParams = new MixParams(utxoHash, utxoIndex, utxoBalance, mixHandler);

PoolInfo poolInfo = ... // user chooses a pool among those fetched earlier
String poolId = poolInfo.getPoolId();
long poolDenomination = poolInfo.getDenomination();

// listener will be notified of whirlpool progress in realtime
WhirlpoolClientListener listener = new LoggingWhirlpoolClientListener(){
   @Override
   public void success(int nbMixs, MixSuccess mixSuccess) {
       super.success(nbMixs, mixSuccess);
       // override with custom code here: all mixs success
   }

   @Override
   public void fail(int currentMix, int nbMixs) {
       super.fail(currentMix, nbMixs);
       // override with custom code here: failure
   }

   @Override
   public void progress(int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
       super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
       // override with custom code here: mix progress
   }

   @Override
   public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
       super.mixSuccess(currentMix, nbMixs, mixSuccess);
       // override with custom code here: one mix success (check if more mixs remaining with currentMix==nbMixs)
   }
};

// start mixing
int nbMixs = 1; // number of mixs to achieve
whirlpoolClient.whirlpool(poolId, poolDenomination, mixParams, nbMixs, listener);
```
