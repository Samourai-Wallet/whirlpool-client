# whirlpool-client integration


## Example


```
// mix handler
ECKey ecKey = ...
BIP47Wallet bip47w = ...
IMixHandler mixHandler = new MixHandler(ecKey, bip47w);

// mix params (input, output)
String utxoHash = ...
Long utxoIndex = ...
String paymentCode = ...
MixParams mixParams = new MixParams(utxoHash, utxoIndex, paymentCode, mixHandler);

// client configuration (server...)
WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, params);

// instanciate client
WhirlpoolClient whirlpoolClient = WhirlpoolClientImpl.newClient(config);

// get pools list
Pools pools = whirlpoolClient.fetchPools();

String poolId = ... // select a pool

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
int mixs = 1; // numer of mixs to achieve

// start mixing
whirlpoolClient.whirlpool(poolId, mixParams, mixs, listener);
```
