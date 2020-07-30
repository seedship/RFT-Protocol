package com.protocoldesigngroup2.xxx.server;

import com.protocoldesigngroup2.xxx.messages.CloseConnection;
import com.protocoldesigngroup2.xxx.messages.Message;
import com.protocoldesigngroup2.xxx.messages.ServerMetadata;
import com.protocoldesigngroup2.xxx.messages.ServerPayload;
import com.protocoldesigngroup2.xxx.network.Endpoint;
import com.protocoldesigngroup2.xxx.network.Network;
import com.protocoldesigngroup2.xxx.utils.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends Thread {
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
                long startTimeMS = LocalTime.now().toNanoOfDay() / 1000;
                if (!clientStateMap.isEmpty()) {
                    // Go through each client state, and send data
                    // Starting from resend entries and then from current offset
                    // Delete entries if last ack received was too long ago
                    for (Map.Entry<Endpoint, ClientState> entry : clientStateMap.entrySet()) {
                        ClientState state = entry.getValue();
                        Endpoint endpoint = entry.getKey();
                        long remainingPackets = state.getTransmissionSpeed();
                        while (remainingPackets > 0) {
                            // For now, timeout after 5 seconds. We can update this later
                            if (LocalTime.now().toNanoOfDay() - state.getLastReceivedAckNS() > 5E9) {
                                // Timeout expired, close session with reason timeout
                                network.sendMessage(new CloseConnection(state.getLastReceivedAckNum(),
                                        new ArrayList<>(), CloseConnection.Reason.TIMEOUT), endpoint);
                                clientStateMap.remove(endpoint);
                                // Move to next connection
                                break;
                            } else if (state.missingChunks.size() > 0) {
                                // Resend entries -- send them first
                                byte[] fileData = new byte[utils.KB];
                                for (Map.Entry<Integer, Set<Long>> resendEntry : state.missingChunks.entrySet()) {
                                    String fileName = state.files.get(resendEntry.getKey()).filename;
                                    RandomAccessFile f = new RandomAccessFile(fileName, "r");
                                    Set<Long> toRemove = new HashSet<>();
                                    for (Long off : resendEntry.getValue()) {
                                        f.seek(off * utils.KB);
                                        int bytesRead = f.read(fileData);
                                        toRemove.add(off);
                                        // NOTE - client can set resend entries from offsets that are beyond the file
                                        // and this will be stuck forever
                                        if (bytesRead != -1) {
                                            network.sendMessage(new ServerPayload(state.getLastReceivedAckNum(),
                                                    new ArrayList<>(), resendEntry.getKey(), off, fileData), endpoint);
                                            remainingPackets--;
                                            if (remainingPackets == 0) {
                                                break;
                                            }
                                        }
                                    }
                                    f.close();
                                    for (Long l : toRemove) {
                                        // Remove sent resend entries outside loop to prevent
                                        // ConcurrentModificationException
                                        resendEntry.getValue().remove(l);
                                    }
                                    if (remainingPackets == 0) {
                                        break;
                                    }
                                }
                            } else if (state.getCurrentFile() >= state.files.size()) {
                                // All files for this client finished and no resend entries. Wait for client to close
                                // Connection or ask for resend.
                                // Move to next connection
                                break;
                            }
                            //Resend Metadata if needed for previous files
                            for (int idx = 0; idx < state.getCurrentFile(); idx++) {
                                if (!state.sentMetadata.get(idx)) {
                                    sendMetadata(idx, state, endpoint);
                                    remainingPackets--;
                                    if (remainingPackets == 0) {
                                        break;
                                    }
                                }
                            }
                            if (remainingPackets == 0) {
                                break;
                            }
                            //Send payload
                            if (!state.sentMetadata.get(state.getCurrentFile())) {
                                // We have not sent metadata
                                sendMetadata(state.getCurrentFile(), state, endpoint);
                                remainingPackets--;
                            } else {
                                // Send Payloads
                                // NOTE this cast might cause problems for big files, but it is how Java defines the
                                // function
                                long off = state.getCurrentOffset();
                                String fileName = state.files.get(state.getCurrentFile()).filename;
                                RandomAccessFile f = new RandomAccessFile(fileName, "r");
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
                                                    fileData),
                                            endpoint);
                                    state.incrementCurrentFileOffset();
                                    remainingPackets--;
                                }
                                f.close();
                            }
                        }
                    }
                }

                long nowMS = LocalTime.now().toNanoOfDay() / 1000;
                if (nowMS - startTimeMS > 0) {
                    sleep(nowMS - startTimeMS);
                }
            } catch (InterruptedException ex) {
                System.out.println("Interrupted by client get request");
            } catch (Exception ex) {
                System.out.println("Unexpected Exception: " + ex);
                ex.printStackTrace();
            }
        }
    }

    private void sendMetadata(int idx, ClientState state, Endpoint endpoint) {
        if (idx >= state.files.size()) {
            System.out.println("Error! offset in sendMetadata bigger than ");
        }
        String fileName = state.files.get(idx).filename;
        byte[] md5 = utils.generateMD5(fileName);
        File f = new File(fileName);
        if (f.exists() && f.isFile()) {
            if (state.files.get(idx).offset >= f.length()) {
                // Offset too big
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.OFFSET_BIGGER_THAN_FILESIZE,
                        idx, f.length(), md5), endpoint);
                state.incrementCurrentFile();
            } else if (f.length() > 0) {
                // Send Metadata as normal
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.DOWNLOAD_NORMAL,
                        idx, f.length(), md5), endpoint);
            } else {
                // Send Metadata with file empty
                network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                        new ArrayList<>(), ServerMetadata.Status.FILE_IS_EMPTY,
                        idx, f.length(), md5), endpoint);
                state.incrementCurrentFile();
            }
        } else {
            // Send Metadata with file does not exist
            network.sendMessage(new ServerMetadata(state.getLastReceivedAckNum(),
                    new ArrayList<>(), ServerMetadata.Status.FILE_DOES_NOT_EXIST,
                    idx, 0L, md5), endpoint);
            state.incrementCurrentFile();
        }
        // Set sent metadata true
        state.sentMetadata.put(idx, true);
    }
}
