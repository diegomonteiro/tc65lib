package org.javacint.at;

//#if sdkns == "siemens"
import com.siemens.icm.io.*;
//#elif sdkns == "cinterion"
//# import com.cinterion.io.*;
//#endif
import org.javacint.logging.Logger;

/**
 * ATCommand wrapper class</br>
 * </br>
 * Generally use send() method with auto newline at the end, in special cases
 * when you need exact ending use sendRaw()</br>
 * If you have a non-interrupting block, you should use getATCommand(), then use
 * that instance (it will be exclusively used by your thread), then run
 * release() method on it.</br>
 * Use sendUrc() to activate some URCs and addListener() to receive them.
 */
public final class ATCommands {

    private static final boolean LOG = false; // We don't want to log everything just because we're in debug
    private static final boolean DEBUG = Logger.BUILD_DEBUG && LOG;
    private static final ATCommandPooled atCommand1, atCommand2;
    private static final ATCommand atCommandURC;
    //private static final ATCommand atCommandData;

    static {
        // We enforce the static final
        ATCommandPooled atc1 = null, atc2 = null;
        ATCommand aturc = null, atdata = null;
        try {
            /*
             * From docs:
             * The available listeners monitor unsolicited AT-Events, changes of serial interface signals (RING, DCD and DSR) and changes of a data connection (CONN).
             * Each started listener thread uses 5 kbytes of RAM.
             * 
             * That's why we use (false, false, ... false)
             */
            atc1 = new ATCommandPooled(new ATCommand(false, false, false, false, false, false));
            atc2 = new ATCommandPooled(new ATCommand(false, false, false, false, false, false));
            aturc = new ATCommand(false, true, false, false, false, false); //One "true" to monitor URC's
            //atdata = new ATCommand(false, false, false, false, false, false);
        } catch (Exception e) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("ATCommands:static " + atc1 + "," + atc2 + "," + aturc + "," + atdata, e);
            }
        }
        atCommand1 = atc1;
        atCommand2 = atc2;
        atCommandURC = aturc;
        //atCommandData = atdata;
    }
    private static final int POOL_MAX_WAIT = 10000; // 10s

    public static synchronized ATCommandPooled getATCommand() {
        if (atCommand1.getBlockingThread() != null && atCommand2.getBlockingThread() != null) { // If we have no available instance, we wait for one
            try {
                ATCommands.class.wait(POOL_MAX_WAIT);
            } catch (InterruptedException ex) {
                if (Logger.BUILD_CRITICAL) {
                    Logger.log(ATCommands.class, ex, 69);
                }
            }
        }
        if (atCommand1.getBlockingThread() == null) {
            atCommand1.setBlockingThread();
            return atCommand1;
        } else if (atCommand2.getBlockingThread() == null) {
            atCommand2.setBlockingThread();
            return atCommand2;
        }
        throw new RuntimeException("Could not get a PooledATCommand");
    }
    private static final int MAXIMUM_NUMBER_OF_TRIES = 10;

    /**
     * Sends an AT command to default ATCommand, seeking "OK" in answer. If no
     * "OK" in the answer, retries some times before returning false
     *
     * @param cmd the AT command to send
     * @return true if success, false otherwise;
     */
    public static boolean sendWhileNotOk(String cmd) {
        for (int i = 0; i < MAXIMUM_NUMBER_OF_TRIES; i++) {
            if (send(cmd).indexOf("OK") >= 0) {
                return true;
            }
            try {
                Thread.sleep(i * 1000); //wait before retry
            } catch (InterruptedException ex) {
                if (Logger.BUILD_WARNING) {
                    ex.printStackTrace();
                }
            }
        }
        return false;
    }

    public static String sendUrc(String cmd) {
        return send(atCommandURC, cmd);
    }

    public static String sendUrcRaw(String cmd) {
        return sendRaw(atCommandURC, cmd);
    }

    /**
     * Generally use this method, it will auto newline at the end, and it will
     * get free ATCommand instance if one is occupied
     *
     * @param cmd the intended AT command
     * @return result
     */
    public static String send(String cmd) {
        return sendRaw(cmd + '\r');
    }

    /**
     * Use this when you need exact AT command ending, not newline, it will be
     * sent 'as-is'
     *
     * @param cmd the intended AT command
     * @return result
     */
    public static String sendRaw(String cmd) {
        ATCommandPooled atc = null;
        try {
            atc = getATCommand();
            return atc.sendRaw(cmd);
        } finally {
            atc.release();
        }
    }

    /**
     * Use this to send AT command to all available parsers.
     *
     * @param cmd the intended AT command
     * @return response of the last parser
     */
    public static String sendAll(String cmd) {
        send(atCommandURC, cmd);
//        send(atCommandData, ATCmd);
        ATCommandPooled atc1 = null, atc2 = null;
        try {
            atc1 = getATCommand();
            atc2 = getATCommand();
            atc1.send(cmd);
            return atc2.send(cmd);
        } finally {
            atc1.release();
            atc2.release();
        }
    }

    private static String atcInstanceToString(ATCommand atc) {
        if (atc == atCommandURC) {
            return "ATURC";
        } /*else if (atc == atCommandData) {
         return "ATData";
         }*/ else if (atc == atCommand1.atc()) {
            return "AT1";
        } else if (atc == atCommand2.atc()) {
            return "AT2";
        } else {
            return "ATUnk";
        }
    }

    //I don't see a reason making it public
    static String send(ATCommand atc, String cmd) {
        return sendRaw(atc, cmd + '\r');
    }

    //I don't see a reason making it public
    static String sendRaw(ATCommand atc, String cmd) {
        try {
            String result;
            if (DEBUG) {
                Logger.log(atcInstanceToString(atc) + " <-- " + cmd);
            }
            synchronized (atc) {
                result = atc.send(cmd);
            }
            if (DEBUG) {
                Logger.log(atcInstanceToString(atc) + " --> " + result);
            }
            return result;
        } catch (Exception ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("ATCommands.sendRaw(" + atc + "," + cmd + ")", ex, true);
            }
            return null;
        }
    }

    public static String sendLong(String cmd) {
        return sendLongRaw(cmd + '\r');
    }

    public static String sendLongRaw(String cmd) {
        ATCommandPooled atc = null;
        try {
            atc = getATCommand();
            return atc.sendLongRaw(cmd);
        } finally {
            atc.release();
        }
    }

    static String sendLongRaw(final ATCommand atc, final String cmd) {
        final StringBuffer str = new StringBuffer(); // We use this as as string reference
        try {
            if (DEBUG) {
                Logger.log(atcInstanceToString(atc) + " <--- " + cmd);
            }
            synchronized (atc) {
                atc.send(cmd, new ATCommandResponseListener() {
                    public void ATResponse(String result) {
                        if (result != null) {
                            str.append(result);
                        }
                        synchronized (atc) {
                            atc.notify();
                        }
                    }
                });
                atc.wait();
            }
        } catch (Exception ex) {
            if (Logger.BUILD_CRITICAL) {
                Logger.log("ATCommands.sendRawLong(" + atc + "," + cmd + ")", ex, true);
            }
        }
        String value = str.length() != 0 ? str.toString() : null;

        if (DEBUG) {
            Logger.log(atcInstanceToString(atc) + " <--- " + value);
        }
        return value;
    }

    public static void addListener(ATCommandListener listener) {
        atCommandURC.addListener(listener);
    }

    public static void removeListener(ATCommandListener listener) {
        atCommandURC.removeListener(listener);
    }
}
