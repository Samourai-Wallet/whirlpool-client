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
java -jar target/whirlpool-client-0.0.1-SNAPSHOT-jar-with-dependencies.jar
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
