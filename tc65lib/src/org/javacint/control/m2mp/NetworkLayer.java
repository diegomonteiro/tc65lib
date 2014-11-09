package org.javacint.control.m2mp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import org.javacint.at.ATExecution;
import org.javacint.common.Bytes;
import org.javacint.common.Strings;
import org.javacint.logging.Logger;
import org.javacint.settings.Settings;
import org.javacint.time.DateManagement;

/**
 * Network layer
 *
 * @author Florent Clairambault / www.webingenia.com
 */
class NetworkLayer implements ISocketLayer {

    private String clientId;
    private SocketConnection socket;
    private InputStream tcpIs;
    private OutputStream tcpOs;
    private long lastDataSendTime = DateManagement.time(), lastDataRecvTime = DateManagement.time();

    void setIdent(String ident) {
        clientId = ident;
    }

    public void setProtoLayer(IProtocolLayer proto) {
    }

    /**
     * Takes care of sending data
     */
    private class NetworkSend implements Runnable {

        final Vector dataOutQueue = new Vector();

        public void run() {
            if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                Logger.log("Started!");
            }
            try {
                while (true) {
                    if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                        Logger.log("...");
                    }
                    try {
                        if (dataOutQueue.size() > 0) {
                            byte[] data = (byte[]) dataOutQueue.elementAt(0);
                            if (send(data)) {
                                dataOutQueue.removeElement(data);
                            } else {
                                connect();
                            }
                        } else {
                            synchronized (dataOutQueue) {
                                dataOutQueue.wait();
                            }
                        }
                    } catch (Exception ex) {
                        if (Logger.BUILD_CRITICAL) {
                            Logger.log("NetworkLayer.NetworkSend.run", ex);
                        }
                        break;
                    }
                }
            } finally {
                if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                    Logger.log("End!");
                }
            }
        }

        /**
         * Sends some data
         *
         * @param data Data to send
         * @return if the data could be sent
         */
        private boolean send(byte[] data) {
            try {
                if (!isConnected()) {
                    return false;
                }

                tcpOs.write(data);
                tcpOs.flush();
                return true;
            } catch (Exception ex) {
                if (Logger.BUILD_WARNING) {
                    Logger.log("NetworkLayer.send", ex);
                }
                return false;
            }
        }

        public void queueSend(byte[] data) {
            if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                Logger.log("NetworkLayer.NetworkSend.QueueSend();");
            }

            synchronized (dataOutQueue) {
                dataOutQueue.addElement(data);
                dataOutQueue.notify();
            }
        }

        public void queueSendFirst(byte[] data) {
            if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                Logger.log("NetworkLayer.NetworkSend.QueueSend();");
            }

            synchronized (dataOutQueue) {
                dataOutQueue.insertElementAt(data, 0);
                dataOutQueue.notify();
            }
        }

        private void stop(boolean b) {
        }
    }

    /**
     * Takes care of receiving data
     */
    private class NetworkReceive implements Runnable {

        private int offset = 0;
        private byte[] frame;
        private int type = 0;
        private byte[] header = new byte[3];

        public void run() {
            if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                Logger.log("Started send !");
            }
            try {
                while (true) {
                    try {

                        if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                            Logger.log("...");
                        }

                        connect();

                        // If we don't yet have a packet
                        if (frame == null) {
                            if (offset == 0) {
                                type = tcpIs.read();
                                header[offset++] = (byte) type;
                                if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                                    Logger.log("_type = " + type + " / " + header[0]);
                                }

                                if (type < IProtocolLayer.NET_MSG_SPECIALIZED_MAX && (type == IProtocolLayer.NET_R_IDENT_RESULT
                                        || type == IProtocolLayer.NET_R_ACK_REQUEST
                                        || type == IProtocolLayer.NET_R_ACK_RESPONSE)) {
                                    frame = new byte[2];
                                    frame[0] = header[0];
                                }
                            } else {
                                int b = tcpIs.read();

                                if (b == -1) {
                                    disconnect();
                                }

                                header[offset++] = (byte) b;

//						if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog) {
//							Logger.log("tcpIn=" + b + " / _offset=" + _offset);
//						}

                                // If it's a one-byte sized message
                                if (offset == 2 && type < IProtocolLayer.NET_MSG_1BYTESIZED_MAX) {
//							if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog) {
//								Logger.log("_type = " + _type + " < " + IProtocolLayer.NET_MSG_1BYTESIZED_MAX);
//							}

                                    int size = Bytes.byteToInt(header[1]) + 2;
                                    frame = new byte[size];
                                    System.arraycopy(header, 0, frame, 0, 2);

                                    if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                                        Logger.log("NetworkLayer.NetworkReceive.Work : size=" + size);
                                    }


                                } // If it's two-bytes sized message
                                else if (offset == 3 && type < IProtocolLayer.NET_MSG_2BYTESSIZED_MAX) {
                                    if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                                        Logger.log("_type = " + type + " < " + IProtocolLayer.NET_MSG_2BYTESSIZED_MAX);
                                    }

                                    int size = Bytes.bytesToShort(header, 1) + 3;
                                    frame = new byte[size];
                                    System.arraycopy(header, 0, frame, 0, 3);

                                    if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                                        Logger.log("NetworkLayer.NetworkReceive.Work : size=" + size);
                                    }
                                }
                            }
                        } else {
                            // We try to read as much as possible
//					if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog) {
//						Logger.log("_offset=" + _offset + " / _frame.length=" + _frame.length);
//					}
                            offset += tcpIs.read(frame, offset, frame.length - offset);
//					if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog) {
//						Logger.log("_offset=" + _offset + " / _frame.length=" + _frame.length);
//					}
                        }
                        // Could be used if we have zero sized messages
                        if (frame != null && offset == frame.length) {
                            try {
                                onFrameReceived(frame);
                            } finally {
                                resetFrame();
                            }
                        }

                    } catch (IOException ex) {
                        if (Logger.BUILD_CRITICAL) {
                            Logger.log("NetworkLayer.NetworkReceiv.work:1", ex);
                        }
                        disconnect();
                        break;
                    } catch (Exception ex) {
                        if (Logger.BUILD_CRITICAL) {
                            Logger.log("NetworkLayer.NetworkReceive.work:2", ex, true);
                        }
                        break;
                    }
                }
            } finally {
                if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                    Logger.log("End!");
                }
            }
        }

        private void resetFrame() {
            if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
                Logger.log("NetworkLayer.NetworkReceive.resetFrame();");
            }
            offset = 0;
            frame = null;
        }

        private void stop(boolean b) {
        }
    }

    /**
     * The constructor
     */
    NetworkLayer() {
        send = new NetworkSend();
        recv = new NetworkReceive();
    }

    void start() {
        new Thread(send, "m2mp-send").start();
        new Thread(recv, "m2mp-recv").start();
    }

    public byte[] identMessage() {
        byte[] rawId = clientId.getBytes();
        byte[] frame = new byte[(rawId.length + 2)];
        frame[0] = ProtocolLayer.NET_S_IDENT;
        frame[1] = (byte) rawId.length;
        System.arraycopy(rawId, 0, frame, 2, rawId.length);
        return frame;
    }
    long lastConnectCall = 0;
    int _nbConnectAttempts = 0;

    /**
     * Connects to a server
     *
     * @return TRUE if the connection worked, FALSE if it didn't
     */
    public synchronized boolean connect() {
        try {
            if (isConnected()) {
                return true;
            }

            if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                Logger.log("NetworkLayer.connect();");
            }

            // We don't want this to be called too soon !
            if (Math.abs(DateManagement.time() - lastConnectCall) < 10) {
                if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                    Logger.log("NetworkLayer.connect : Too soon !");
                }
                Thread.sleep(1000);
                return false;
            }


            if (++_nbConnectAttempts % 3 == 0) {
                if (Logger.BUILD_NOTICE && M2MPClientImpl.m2mpLog_) {
                    Logger.log("NetworkLayer.connect : We need to quit and reconnect to the GSM Network...");
                }
                try {
                    ATExecution.setAirplaneMode(true);
                    Thread.sleep(5000);
                    ATExecution.setAirplaneMode(false);
                    Thread.sleep(15000);
                } catch (Exception ex) {
                    if (Logger.BUILD_CRITICAL) {
                        Logger.log("NetworkLayer.connect:296", ex);
                    }
                }
                if (Logger.BUILD_NOTICE && M2MPClientImpl.m2mpLog_) {
                    Logger.log("NetworkLayer.connect : Done...");
                }
            }

            //OnDisconnected();

            // We have to make sure receiving won't be messed up
            // by last connection
            recv.resetFrame();

            try {
                String servers[] = Strings.split(',', Settings.get(M2MPClientImpl.SETTING_M2MP_SERVERS));
                for (int i = 0; i < servers.length; ++i) {
                    String server = servers[i];
                    try {
                        socket = (SocketConnection) Connector.open("socket://" + server);
                        //_socket.setSocketOption(SocketConnection.KEEPALIVE, 1);
                        tcpIs = socket.openInputStream();
                        tcpOs = socket.openOutputStream();

                        if (Logger.BUILD_NOTICE && M2MPClientImpl.m2mpLog_) {
                            Logger.log("NetworkLayer.connect : Successfully connected to " + server);
                        }

                        tcpOs.write(identMessage());
                        //CheckIfSendVersionChange();
                        tcpOs.flush();

                        if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
                            Logger.log("NetworkLayer.connect : Identification request sent...");
                        }

                        lastDataRecvTime = DateManagement.time();
                        lastDataSendTime = DateManagement.time();

                        _nbConnectAttempts = 0;
                        return true;
                    } catch (Exception ex) {
                        if (Logger.BUILD_WARNING) {
                            Logger.log("NetworkLayer.connect:314 : Could not connect to " + server, ex);
                        }
                    }
                }



                // It means we don't have any server
                return false;
            } catch (Exception ex) {
                if (Logger.BUILD_WARNING) {
                    Logger.log("NetworkLayer.connect:332", ex);
                }
                return false;
            } finally {
                try {
                    Thread.sleep(10000);
                } catch (Exception ex) {
                }
                lastConnectCall = DateManagement.time();
            }
        } catch (Exception ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("NetworLayer.connect:344", ex);
            }
            return false;
        }
    }

    /**
     * Disconnects from the server
     */
    public void disconnect() {
        try {
            if (tcpIs != null) {
                tcpIs.close();
            }
        } catch (Exception ex) {
            Logger.log("NetworkLayer.Disconnect.tcpIn.close();", ex);
        }

        try {
            if (tcpOs != null) {
                tcpOs.close();
            }
        } catch (Exception ex) {
            Logger.log("NetworkLayer.Disconnect.tcpIn.close();", ex);
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ex) {
            Logger.log("NetworkLayer.Disconnect.tcpIn.close();", ex);
        }

        socket = null;
        tcpIs = null;
        tcpOs = null;

        onDisconnected();
    }

    public boolean isConnected() {
        return socket != null;
    }

    void dispose() {
        recv.stop(false);
        send.stop(false);
        disconnect();
    }
    private final NetworkSend send;
    private final NetworkReceive recv;

    /**
     * Send a frame
     *
     * @param data frame data
     */
    public void sendFrame(byte[] data) {
        if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
            Logger.log("NetworkLayer.sendFrame( " + Bytes.byteArrayToPrettyString(data) + " );");
        } else if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
            Logger.log("NetworkLayer.sendFrame( byte[" + data.length + "] );");
        }

        lastDataSendTime = DateManagement.time();

        send.queueSend(data);
    }

    /**
     * Send frames on the top of the
     *
     * @param data Data
     */
    public void sendFrameFirst(byte[] data) {
        if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
            Logger.log("NetworkLayer.SendFrameFirst( " + Bytes.byteArrayToPrettyString(data) + " );");
        } else if (Logger.BUILD_VERBOSE && M2MPClientImpl.m2mpLog_) {
            Logger.log("NetworkLayer.SendFrameFirst( byte[" + data.length + "] );");
        }

        lastDataSendTime = DateManagement.time();

        send.queueSendFirst(data);
    }

    protected void onFrameReceived(byte[] data) {
        if (Logger.BUILD_DEBUG && M2MPClientImpl.m2mpLog_) {
            Logger.log("NetworkLayer.NetworkReceive.onFrameReceived( " + Bytes.byteArrayToPrettyString(data) + " )");
        }

        updateLastRecvTime();

        if (protocolLayer != null) {
            protocolLayer.receivedFrame(data);
        }

    }

    public long getLastRecvTime() {
        return lastDataRecvTime;
    }

    public void updateLastRecvTime() {
        lastDataRecvTime = DateManagement.time();
    }

    public long getLastSendTime() {
        return lastDataSendTime;
    }

    public long getLastConnectCall() {
        return lastConnectCall;
    }

    protected void onDisconnected() {
        if (protocolLayer != null) {
            protocolLayer.disconnected();
        }
    }
    private ProtocolLayer protocolLayer;

    public void setProtoLayer(ProtocolLayer proto) {
        protocolLayer = proto;
    }
}
