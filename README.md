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
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --network={main,test} --utxo= --utxo-key= --seed-passphrase= --seed-words= [--server=host:port] [--debug]
```

Examples:
```
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --utxo=5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2-3 --utxo-key=cN27hV14EEjmwVowfzoeZ9hUGwJDxspuT7N4bQDz651LKmqMUdVs --seed-passphrase=all10 --network=test --seed-words="all all all all all all all all all all all all"
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-run.jar --utxo=7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16-2 --utxo-key=cUwS52vEv4ursFBdGJWgHiZyBNqqSF5nFTsunUpocRBYGLY72z4j --seed-passphrase=all10 --network=test --seed-words="all all all all all all all all all all all all"
```


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
