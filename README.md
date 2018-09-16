# whirlpool-client
Whirlpool client

## Build instructions
Before using whirlpool-client, install dependencies in this order:
- whirlpool-protocol
- whirlpool-client

```
cd whirlpool-protocol
mvn clean install -Dmaven.test.skip=true

cd whirlpool-client
mvn clean install -Dmaven.test.skip=true

```

## Integration (developers)
See [README-DEV.md](README-DEV.md)