package org.javacint.sms;

/**
 * Base SMS Consumer interface, used to respond to incoming SMSs
 */
public interface SMSConsumer {

    /**
     * Method called by SMS receiver.
     * Each consumer should return true once they consider it is of no other
     * consumer's interest. It works the same with command receivers.
     *
     * @param msg Message
     * @return true once the consumer consider it is of no other consumer's
     * interest, false otherwise
     */
    public boolean smsReceived(Message msg);
}
