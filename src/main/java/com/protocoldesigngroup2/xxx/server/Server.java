package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerMetadata;
import com.protocoldesigngroup2.xxx.messages.ServerPayload;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.Network;
import com.protocoldesigngroup2.xxx.utils.utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends Thread {
    // Server will wait 3 seconds
    public static final long EXPIRE_TIME = 3000L;
    public static final long PERIOD_MS = 1000L;
    public final Map<Endpoint, ClientState> clientStateMap;
    public final Network network;

    public Server(float p, float q, int port) {
        network = Network.createServer(p, q, port);
        clientStateMap = new ConcurrentHashMap<>();
        if (network == null) {
            return;
        }
        network.addCallbackMethod(Message.Type.CLIENT_REQUEST, new ClientRequestHandler(this));
        network.addCallbackMethod(Message.Type.CLIENT_ACK, new ClientAckHandler(this));
        network.addCallbackMethod(Message.Type.CLOSE_CONNECTION, new CloseConnectionHandler(this));
    }

    @Override
    public void run() {
        if (network == null) {
            System.out.println("Server: Network is null, exiting");
            return;
        }

        // Start the network
        new Thread(() -> {
            this.network.listen();
        }).start();

        while (true) {
            try {
                long startTimeMS = System.currentTimeMillis();
                utils.printDebug("Entering service loop. Active Sessions: " + clientStateMap.size());
                if (!clientStateMap.isEmpty()) {
                    // Go through each client state, and send data
                    // Starting from resend entries and then from current offset
                    // Delete entries if last ack received was too long ago
                    removeExpiredClients();
                    for (Map.Entry<Endpoint, ClientState> entry : clientStateMap.entrySet()) {
                        ClientState state = entry.getValue();
                        Endpoint endpoint = entry.getKey();
                        serviceClient(state, endpoint);
                    }
                }

                long nowMS = System.currentTimeMillis();
                long sleepMS = PERIOD_MS - (nowMS - startTimeMS);
                utils.printDebug("Finishing service loop, sleeping for " + sleepMS + " ms.");
                if (sleepMS > 0) {
                    sleep(sleepMS);
                }
            } catch (Exception ex) {
                System.out.println("Unexpected Exception: " + ex);
                ex.printStackTrace();
            }
        }
    }

    private void removeExpiredClients() {
        List<Endpoint> expired = new ArrayList<>();
        for (Map.Entry<Endpoint, ClientState> entry : clientStateMap.entrySet()) {
            ClientState state = entry.getValue();
            Endpoint endpoint = entry.getKey();
            long lastHeardFrom = System.currentTimeMillis() - state.getLastReceivedAckMS();
            long ttl = EXPIRE_TIME - lastHeardFrom;
            utils.printDebug(endpoint + " TTL is " + ttl + ".");
            if (ttl < 0) {
                // Timeout expired, close session with reason timeout
                expired.add(endpoint);
            }
        }
        for (Endpoint endpoint : expired) {
            // Timeout expired, close session with reason timeout
            ClientState state = clientStateMap.remove(endpoint);
            network.sendMessage(new CloseConnection(state.getLastReceivedAckNum(),
                    new ArrayList<>(), CloseConnection.Reason.TIMEOUT), endpoint);
            state.closeAllFiles();
            utils.printDebug(endpoint + " has timed out");
        }
    }

    private void serviceClient(ClientState state, Endpoint endpoint) throws IOException {
        long remainingPackets = state.getTransmissionSpeed();
        while (remainingPackets > 0) {
            if (state.missingChunks.size() > 0) {
                // Resend entries -- send them first
                byte[] fileData = new byte[utils.KB];
                for (Map.Entry<Integer, Set<Long>> resendEntry : state.missingChunks.entrySet()) {
                    RandomAccessFile f = state.getFileAccess(resendEntry.getKey());
                    Set<Long> toRemove = new HashSet<>();
                    for (Long off : resendEntry.getValue()) {
                        // NOTE - client can set resend entries from files that do not exist and this
                        // will be stuck forever
                        toRemove.add(off);
                        if (f != null) {
                            f.seek(off * utils.KB);
                            int bytesRead = f.read(fileData);
                            // NOTE - client can set resend entries from offsets that are beyond the
                            // file and this will be stuck forever
                            if (bytesRead != -1) {
                                utils.printDebug("Sending payload for file " + resendEntry.getKey() + " at offset " + off + " with size " + fileData.length + ".");
                                network.sendMessage(new ServerPayload(state.getLastReceivedAckNum(),
                                                new ArrayList<>(), resendEntry.getKey(), off, fileData, bytesRead),
                                        endpoint);
                                remainingPackets--;
                                if (remainingPackets == 0) {
                                    break;
                                }
                            }
                        }
                    }
                    for (Long l : toRemove) {
                        // Remove sent resend entries outside loop to prevent
                        // ConcurrentModificationException
                        resendEntry.getValue().remove(l);
                    }
                    if (remainingPackets == 0) {
                        return;
                    }
                }
                // Finish processing resend entries

            }
            // Resend Metadata if needed for previous files
            for (int idx = 0; idx < state.getCurrentFile(); idx++) {
                if (!state.sentMetadata.get(idx)) {
                    sendMetadata(idx, state, endpoint);
                    remainingPackets--;
                    if (remainingPackets == 0) {
                        return;
                    }
                }
            }
            if (state.getCurrentFile() >= state.files.size()) {
                // All files for this client finished and no resend entries. Wait for client to close
                // Connection or ask for resend.
                // Move to next connection
                return;
            }
            // Send payload
            if (!state.sentMetadata.get(state.getCurrentFile())) {
                // We have not sent metadata
                boolean nonNormalMetadata = sendMetadata(state.getCurrentFile(), state, endpoint);
                if (nonNormalMetadata) {
                    // File should be skipped
                    state.incrementCurrentFile();
                }
                remainingPackets--;
            } else {
                // Send Payloads
                long off = state.getCurrentOffset();
                RandomAccessFile f = state.getFileAccess(state.getCurrentFile());
                utils.printDebug("Sending payload for file " + state.getCurrentFile() + " at offset " + off + ".");
                // If file did not exist, file number should have been incremented in send metadata
                assert f != null;
                f.seek(off * utils.KB);
                byte[] fileData = new byte[utils.KB];
                int bytesRead = f.read(fileData);
                if (bytesRead == -1) {
                    // reached EOF, move to next file
                    state.incrementCurrentFile();
                } else {
                    // Otherwise send payload
                    network.sendMessage(new ServerPayload(state.getLastReceivedAckNum(),
                                    new ArrayList<>(), state.getCurrentFile(), state.getCurrentOffset(),
                                    fileData, bytesRead),
                            endpoint);
                    state.incrementCurrentFileOffset();
                    remainingPackets--;
                }
            }
            utils.printDebug("Remaining packets: " + remainingPackets);
        }
    }

    // Returns true if non normal metadata was sent, otherwise returns false
    private boolean sendMetadata(int idx, ClientState state, Endpoint endpoint) throws IOException {
        assert idx < state.files.size();
        String fileName = state.files.get(idx).filename;
        byte[] md5 = utils.generateMD5(fileName);
        assert md5 != null;
        RandomAccessFile f = state.getFileAccess(idx);
        // Set sent metadata true
        state.sentMetadata.put(idx, true);
        if (f != null) {
            if (f.length() == 0) {
                // Send Metadata with file empty
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.FILE_IS_EMPTY,
                        idx, f.length(), md5), endpoint);
                utils.printDebug("Sending File Empty Metadata for file " + idx);
                return true;
            } else if (state.files.get(idx).offset >= f.length()) {
                // Offset too big
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.OFFSET_BIGGER_THAN_FILESIZE,
                        idx, f.length(), md5), endpoint);
                utils.printDebug("Sending Offset Too Big Metadata for file " + idx);
                return true;
            } else {
                // Send Metadata as normal
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.DOWNLOAD_NORMAL,
                        idx, f.length(), md5), endpoint);
                utils.printDebug("Sending Normal Metadata for file " + idx);
                return false;
            }
        } else {
            // Send Metadata with file does not exist
            network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                    new ArrayList<>(), ServerMetadata.Status.FILE_DOES_NOT_EXIST,
                    idx, 0L, md5), endpoint);
            utils.printDebug("Sending File Does Not exist Metadata for file " + idx);
            return true;
        }
    }
}
