# whirlpool-client integration


## Integration example


```
// mix handler
ECKey ecKey = ...
BIP47Wallet bip47w = ...
IMixHandler mixHandler = new MixHandler(ecKey, bip47w);

// mix params
String utxoHash = ...
Long utxoIndex = ...
String paymentCode = ...
boolean liquidity = ...
MixParams mixParams = new MixParams(utxoHash, utxoIndex, paymentCode, mixHandler, liquidity);

// configure client
WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, params);

// instanciate client
WhirlpoolClient whirlpoolClient = new WhirlpoolClient(config);

// run
WhirlpoolClientListener listener = ... // will be notified of mix status in realtime
whirlpoolClient.whirlpool(roundParams, rounds, listener);
```
