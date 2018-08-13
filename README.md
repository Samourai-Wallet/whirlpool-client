# whirlpool-client
Whirlpool client

## Usage
- (run whirlpool-server if not already running)

- run from IDE:
```
com.samourai.whirlpool.client.Application
```

- run from commandline:
```
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --network={main,test} --utxo= --utxo-key= --utxo-balance= --seed-passphrase= --seed-words= [--mixs=1] [--pool=] [--server=host:port] [--debug]
```

Examples:
```
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --utxo=5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2-3 --utxo-key=cN27hV14EEjmwVowfzoeZ9hUGwJDxspuT7N4bQDz651LKmqMUdVs --utxo-balance=100001000 --seed-passphrase=all10 --network=test --seed-words="all all all all all all all all all all all all --pool=1btc"
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --utxo=7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16-2 --utxo-key=cUwS52vEv4ursFBdGJWgHiZyBNqqSF5nFTsunUpocRBYGLY72z4j --utxo-balance=100001000 --seed-passphrase=all10 --network=test --seed-words="all all all all all all all all all all all all --pool=1btc"
```

Arguments:
- server: (host:port) server to connect to
- network: (main,test) bitcoin network to use. Client will abort if server runs on a different network.
- utxo: (txhash:indice) input to provide
- utxo-key: ECKey to sign the input
- utxo-balance: utxo balance in satoshis. Whole utxo-balance balance will be spent.
- seed-passphrase and seed-words: wallet seed from which to derive the paynym for computing output address to receive the funds
- mixs: (1 to N) number of mixes to complete. Client will keep running (as a liquidity) until completing this number of mixes.
- pool: id of the pool to join.
- debug: (true/false) display more logs for debugging


## Build instructions
Before using whirlpool-client, install dependencies in this order:
- bitcoinj (latest samourai version)
- ExtLibJ
- whirlpool-protocol
- whirlpool-client
- whirlpool-server

```
cd bitcoinj
mvn clean install -Dmaven.test.skip=true

cd ExtLibJ
mvn clean install -Dmaven.test.skip=true

cd whirlpool-protocol
mvn clean install -Dmaven.test.skip=true

cd whirlpool-client
mvn clean install -Dmaven.test.skip=true

cd whirlpool-server
mvn clean install -Dmaven.test.skip=true

```
