# XXX protocol Java implementation

This is the java implemenation of the [XXX protocol](https://rfc.lennardn.xyz). This repository is a public fork of [this one](https://gitlab.lrz.de/ga96xic/xxx-protocol)

A video of this Java implementation of the protocol in action can be found [here](https://codimd.lennardn.xyz/FozGSErxSYu41b3-hre_SA#)

## Build

To build it, you need [Apache Maven](https://maven.apache.org) installed. Then you can call `mvn package` to build it and run all tests. The final executable will be in the target folder.

## Usage

To start an XXX server, you can use the following command:

```
rft -s [-t <port>] [-p <p>] [-q <q>] [-v]
```

To start an XXX client and connect to a server, you can use the following
command:

```
rft <host> [-t <port>] [-p <p>] [-q <q>] [-v] <file> ...
```

* `-s`: Starts the program in server mode
* `<host>`: Specifies the hostname of the server, the client wants to connect to
* `-t`: Specifies the port of the server. If no port is specified, the default
  port 2020 is used
* `-p`/`-q`: Specifies the loss probabilities for a Markow-chain model. If only
  one is specified, `p=q` is assumed. If neither is specified, `p=q=0` is
  assumed
* `-v`: Starts the client/server in verbose mode
* `<file>`: Specifies one multiple files the client wants to download from the
  server
