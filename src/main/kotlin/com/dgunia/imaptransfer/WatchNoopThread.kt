package com.dgunia.imaptransfer

import com.sun.mail.imap.IMAPFolder
import java.time.Duration
import java.util.*
import java.util.logging.Logger
import javax.mail.MessagingException

/**
 * Runs the IMAP NOOP command every five minutes to ensure that the IMAP connection is not closed. Reconnects if necessary.
 */
class WatchNoopThread(val imapFolder: IMAPFolder, val reconnect: () -> IMAPFolder) : Runnable {
    override fun run() {
        val lastReconnect = Date();
        try {
            while (!Thread.interrupted()) {
                Thread.sleep(5 * 60 * 1000)

                // Perform a NOOP to keep the connection alive
                imapFolder.doCommand { p ->
                    p.simpleCommand("NOOP", null)
                    null
                }
                Logger.getGlobal().warning("NOOP command");

                // Jede Stunde einmal neu verbinden
                if (lastReconnect.toInstant().plus(Duration.ofHours(1)).isBefore(Date().toInstant())) {
                    Logger.getGlobal().warning("Reconnect source after an hour");
                    imapFolder.close()
                    reconnect()
                    break
                }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: MessagingException) {
            Logger.getGlobal().warning("Unexpected IMAP IDLE exception " + e.localizedMessage)

            reconnect()
        } catch (e: Exception) {
            Logger.getGlobal().warning("NOOP thread exception " + e.localizedMessage)
            e.printStackTrace()

            Thread.sleep(1 * 60 * 1000) // wait a minute then try to connect again

            reconnect()
        }
    }
}
