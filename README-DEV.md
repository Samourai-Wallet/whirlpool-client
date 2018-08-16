# whirlpool-client integration


## Integration example


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

// run
WhirlpoolClientListener listener = ... // will be notified of mix status in realtime
int mixs = 1; // numer of mixs to achieve
whirlpoolClient.whirlpool(poolId, mixParams, mixs, listener);
```
