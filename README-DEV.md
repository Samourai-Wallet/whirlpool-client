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
MixParams mixParams = new MixParams(utxoHash, utxoIndex, paymentCode, mixHandler);

// configure client
WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, params);

// instanciate client
WhirlpoolClient whirlpoolClient = new WhirlpoolClient(config);

// run
WhirlpoolClientListener listener = ... // will be notified of mix status in realtime
whirlpoolClient.whirlpool(mixParams, rounds, listener);
```
