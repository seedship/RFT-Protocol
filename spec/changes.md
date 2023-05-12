# Base Protocol
## Server Center Pro (XXX):
* Smaller latency after first server message (especially if first packet gets lost)
* Server has more control e.g. for optimization
* Less data overhead as client does not need to tell server which blocks to send
* More familiar, i.e., more similarity with TCP

## Client Center Pro (RFT):
* Simpler design: requesting new data and re-requesting lost packets work in the same way

# Versioning
## Pro Explicit versioning
* easier regarding implementation
* Fields after the version can be changed in length, but version can still be interpreted

## Pro Version in Options
* More flexible
* Suitable for "nice-to-have" features which not always have to be included (minimizes space consumption)

## Solution
* Keep version byte
* Use option for "nice to have" features like compression (similar to RTF protocol)


# Increasing packet size vs sending rate
* Con packet size: IP fragmentation (one UDP packet is split into multiple IP packets). Using one IP packet means loosing the whole split-up UPD packet.

# Changes (based on XXX protocol)
## General
* Keep version byte, add options (similar to RFT protocol)
* Options need to be in the meta data, in the client request and maybe in the payload and maybe in the ACK
* Add the sentence: All reserved Bytes/Bits MUST be 0.
* Maximum Packet size of 1460B
* Define chunk size: every packet this many bytes. 1kb? 1460B? Perform some tests.
* Define offsets etc. in chunks instead of byte

## RTT
* The client can get a first RTT estimate by measuring the time between its' initial file request and the server's metadata response.
* Updating RTT during transmission: Client ACK and server payload messages have an 8-bit ACK-number field. This field is increased by one by each new ACK until the counter wraps around (255->0). The server puts the ACK-number of the last received ACK into every server payload message. The client can calculate a new RTT by calculating the difference between the time at which an ACK is sent and the time at which the first payload packet with the the same ACK number is received.
* The server can not directly measure the RTT. Instead, it can estimate the RTT that the client measured. The client sends an ACK every RTT/4 seconds. The server can measure the time between every fourth ACK and average those results to estimate the RTT.

## Congestion Control
* Congestion Control: Every ACK with serviced (not repeated) resend entries, the sending rate will decrease. Otherwise speed will increase.
* Implementation: Using a ring buffer as big as the maximum number of chunks as well as the lower and the upper limit. Then we remember the last ACK position in this ring buffer.
* Head of the ring buffer: The highest offset of what we have received so far; Tail of the ring buffer: The start of the oldest gap in the data.
* Server keeps track of resend entries and only sends them every 4 times (according to RTT and ACK sending rate) it receives them. 
* Client includes all resend entries in every ACK from the tail to the last ACK position (to prevent requesting out-of-order packets) and then updates the last ACK position.

## Flow control
* Flow Control: In Client request and in Client ACK message one field is gonna be the maximum transmission rate (32 bit integer, unit is "number of chunks"). The server MUST transmit at most that many payload packets per second. If this value is 0, there is no limit.
* The server MUST update the maximum transmission rate with each received packet.

## Message Layout
* The request ID is removed from all messages. Instead of mapping messages to connections by using the request ID, now the tuple (client IP, client port is used as an unique identifier.
* For every request message of the client, the client SHOULD use a new client-port, to have a file uniquely identifiable with the tuple of the file number and the client port.

### Global Header
* The header is now only 2 bytes long, followed by a variable length header.
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Ver  | MessagId  | # Options |           Options            ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

* Options (type-length-value format):
* Opt Length in Bytes
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Option Type  |  Opt Length   |          Option Value        ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Client Request
* Message ID: 0x0
* Everything is behind the global header
* Maximum transmission rate in chunks (so in kB)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  Maximimum transmission rate                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           File Count          |      File Descriptors         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               |
|                       [variable length]                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
* File Descriptors:
* Filename length in Bytes as well
 ```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                            Offset                            ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...                                              |Filename Len ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
... Filename Len|                                               |
+-+-+-+-+-+-+-+-+                                               |
|                           Filename                            |
|                       [variable length]                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
### Server Metadata
* Message ID: 0x1
* Checksum: Might be SHA256, SHA1, MD5, CRC, ... (waiting for benchmarks)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Pad\Reserved |     Status    |          File Number          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       File Size (in byte)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                            Checksum                           |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
### Server Payload
* Message ID: 0x2
* offset is in chunks
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          File Number          |  ACK Number   |    Offset    ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...                          Offset                            ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...  Offset     |              Payload                         ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...                          Payload                            |
|                                                               |
|                       [variable length]                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
## Client ACK
* Message ID: 0x3
* TODO add maximum throughput field
* TODO add ACK number field
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    ACK Number |             File Number       |     Status    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  Maximum transmission rate                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        Offset of file "file number" in chunks                ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                                                |   Resend     ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...                         ... Entries                         |
|                                                               |
|                       [variable length]                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Resend entry:
* Length is 1 byte large for burst errors. If more packets are missing, another resend entry is needed
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       File number             |                              ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...                         Offset                             ...
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
...             |   Length      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 3.7 Close Connection
* Message ID: 0x4
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Reason              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
Reasons:
* `0x0` - Unspecified/No Reason Provided
* `0x1` - Application closed (ctrl^c)
* `0x2` - Unsupported Version
* `0x3` - Unknown RequestID
* `0x4` - Wrong Checksum (client to server)
* `0x5` - Download Finished (client to server)
* `0x6` - Timeout
* `0x7` - File too small
