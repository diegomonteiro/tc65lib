package org.javacint.apnauto;

import org.javacint.common.Strings;
import org.javacint.logging.Logger;

/**
 * GPRS Settings wrapper. This class is optimized for fast GPRS files parsing.
 */
public final class GPRSSettings {

    private final static String EMPTY = "";
    /**
     * Default target.
     * It's Google's DNS 1 server using DNS/TCP.
     */
    public final static String DEFAULT_TARGET = "8.8.8.8:53";
    private String[] values;
    private int index_carrier = -1,
            index_apn = -1,
            index_user = -1,
            index_pass = -1,
            index_dns = -1,
            index_target = -1;

    /**
     * Direct access to parameters values.
     *
     * @param values
     */
    public void setValues(String[] values) {
        this.values = values;
    }

    private String get(int index) {
        return get(index, EMPTY);
    }

    private String get(int index, String alt) {
        if (index == -1 || index >= values.length) {
            return alt;
        } else {
            return values[index];
        }
    }

    /**
     * Get the carrier. "gprs" or "gsm"
     *
     * @return Carrier
     */
    public String getCarrier() {
        return get(index_carrier);
    }

    /**
     * Get the APN.
     *
     * @return APN
     */
    public String getApn() {
        return get(index_apn);
    }

    /**
     * Get the APN user.
     *
     * @return APN user
     */
    public String getUser() {
        return get(index_user);
    }

    /**
     * Get the APN password.
     *
     * @return APN password
     */
    public String getPass() {
        return get(index_pass);
    }

    /**
     * Get the DNS.
     *
     * @return DNS
     */
    public String getDns() {
        return get(index_dns);
    }

    /**
     * Get the target to test the APN on.
     *
     * @return Target
     */
    public String getTarget() {
        return get(index_target, DEFAULT_TARGET);
    }

    /**
     * Parse a GPRS settings line.
     *
     * @param line To parse
     */
    public void parse(String line) {
        if (line == null) {
            return;
        }
        values = Strings.split('|', line);
    }

    /**
     * Set columns parameters.
     *
     * When the columns definition is loaded, each column gets a specific
     * meaning.
     *
     * @param cols Set the columns.
     */
    public void setColumns(String cols) {
        String[] spl = Strings.split('|', cols);
        int l = spl.length;
        for (int i = 0; i < l; i++) {
            String col = spl[i];
            if (col.equals("c")) {
                index_carrier = i; // carrier: "gprs","gsm"
            } else if (col.equals("a")) {
                index_apn = i; // apn: "internet"
            } else if (col.equals("u")) {
                index_user = i; // user: "default"
            } else if (col.equals("p")) {
                index_pass = i; // pass: "default"
            } else if (col.equals("d")) {
                index_dns = i; // dns: "8.8.8.8"
            } else if (col.equals("t")) {
                index_target = i; // target: "8.8.8.8:53" (host to try)
            } else if (Logger.BUILD_CRITICAL) {
                Logger.log("APNS_FILE: Column \"" + col + "\" (" + i + ") is unknown!");
            }
        }
    }

    public String toString() {
        return toSjnet(); //getCarrier() + " --> " + getApn() + ", " + getUser() + ", " + getPass();
    }
    /**
     * Parameter enclosing
     */
    private static final char QT = '"';
    /**
     * Parameters separator
     */
    private static final char SE = ',';

    /**
     * Export the GPRS settings to string. Export the GPRS settings to an
     * AT^SJNET
     *
     * @return String that can be applied as-is to AT^SJNET
     */
    public String toSjnet() {
        return QT + "gprs" + QT + SE
                + QT + getApn() + QT + SE
                + QT + getUser() + QT + SE
                + QT + getPass() + QT + SE
                + QT + getDns() + QT + SE
                + "0";
    }
}
