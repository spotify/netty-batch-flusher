[![Build Status](https://travis-ci.org/spotify/netty-batch-flusher.png?branch=master)](https://travis-ci.org/spotify/netty-batch-flusher)

An implementation of natural batching for netty channels to gather writes into fewer syscalls.

## Usage

Replace calls to `Channel.flush()` and `ChannelHandlerContext.flush()` with calls to `BatchFlusher.flush()`.
`BatchFlusher` will then gather multiple flushes into fewer calls to `Channel.flush()`.

At that point the actual gathering write can be left to Netty to perform using the underlying `GatheringByteChannel`.
Alternatively the `write()` and `flush()` methods of `ChannelOutboundHandler` can be implemented to perform custom e.g.
`ByteBuf` consolidation.

### `pom.xml`

```xml
<dependency>
  <groupId>com.spotify</groupId>
  <artifactId>netty-batch-flusher</artifactId>
  <version>0.1.0</version>
</dependency>
```

## License

This software is licensed using the Apache 2.0 license. Details in the file LICENSE
