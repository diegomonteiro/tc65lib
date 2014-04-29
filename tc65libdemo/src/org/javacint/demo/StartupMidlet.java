package org.javacint.demo;

import javax.microedition.midlet.*;
import org.javacint.logging.Logger;
import org.javacint.task.Timers;

/**
 * Startup midlet. The midlet is only a receiver for the start/destroy events
 */
public class StartupMidlet extends MIDlet {

    public void startApp() {
        try {
            String version = getAppProperty("MIDlet-Version");
            System.out.print("Demo v" + version + "\r");
            // The first task is to plan a loading task.
            Timers.getSlow().schedule(new StartupLoader(version), 0);
        } catch (Throwable ex) {
            Logger.log(this + ".startApp", ex);
        }
        // We're now leaving startApp (and releasing the starting thread)
    }

    public void pauseApp() {
        // Not supported in the TC65i
    }

    public void destroyApp(boolean unconditional) {
        new ShutdownLoader(unconditional)
                // We execute it directly to only notify the OS that we finished
                // once it has finished executing.
                .run();
        notifyDestroyed();
    }

    public String toString() {
        return "Midlet";
    }
}
