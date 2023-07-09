package com.dgunia.imaptransfer

import com.sun.mail.imap.IMAPFolder
import java.time.Duration
import java.util.*
import java.util.logging.Logger
import javax.mail.MessagingException

/**
 * Runs the IMAP NOOP command every five minutes to ensure that the IMAP connection is not closed. Reconnects if necessary.
 */
class WatchNoopThread(var imapFolder: IMAPFolder, val reconnect: () -> IMAPFolder) : Runnable {
    override fun run() {
        var lastReconnect = Date();
        while (!Thread.interrupted()) {
            try {
                Thread.sleep(5 * 60 * 1000)

                // Perform a NOOP to keep the connection alive
                imapFolder.doCommand { p ->
                    p.simpleCommand("NOOP", null)
                    null
                }
                Logger.getGlobal().warning("NOOP idle command");

                // Jede Stunde einmal neu verbinden
                if (lastReconnect.toInstant().plus(Duration.ofHours(1)).isAfter(Date().toInstant())) {
                    Logger.getGlobal().warning("Reconnect source after an hour");
                    lastReconnect = Date()
                    imapFolder = reconnect()
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: MessagingException) {
                Logger.getGlobal().warning("Unexpected IMAP IDLE exception " + e.localizedMessage)

                imapFolder = reconnect()
            } catch (e: Exception) {
                Logger.getGlobal().warning("NOOP thread exception " + e.localizedMessage)
                e.printStackTrace()

                Thread.sleep(1 * 60 * 1000) // wait a minute then try to connect again

                imapFolder = reconnect()
            }
        }
    }
}
