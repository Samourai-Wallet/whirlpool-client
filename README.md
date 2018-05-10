# whirlpool-client
Whirlpool client

## Usage
- edit whirlpool-server configuration (
- run whirlpool-server (run Application.java)
- run whirlpool-client(s)

## Build instructions
Before using whirlpool-client, install dependencies in this order:
- bitcoinj (latest samourai version)
- ExtLibJ
- whirlpool-protocol
- whirlpool-client
- whirlpool-server

```
cd bitcoinj
mvn install -Dmaven.test.skip=true

cd ExtLibJ
mvn install -Dmaven.test.skip=true

cd whirlpool-protocol
mvn install -Dmaven.test.skip=true

cd whirlpool-client
mvn install -Dmaven.test.skip=true

cd whirlpool-server
mvn install -Dmaven.test.skip=true

```
