package com.dgunia.imaptransfer

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import java.io.*
import java.util.logging.Logger
import java.util.zip.GZIPInputStream
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.FolderClosedException
import javax.mail.Session
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.MimeMessage

/**
 * Reads emails from an IMAP server or a local directory.
 */
interface MessagesProvider {
    /**
     * Connects to the server. Must be called before #sync can be used.
     */
    fun connect()

    /**
     * Read the messages and send them to messagesReceiver. If watch is true this function will block.
     * It will keep an IMAP connection open and will immediately load new messages when they arrive.
     */
    fun sync(messagesReceiver: MessagesReceiver, watch: Boolean)
}

/**
 * Reads emails from an IMAP server.
 */
class ImapProvider(val config: ImapConfig, val move: Boolean = false, val syncMode: ImapSyncMode) : MessagesProvider {
    private val session: Session = Utils.getIMAPSSession()
    private var inbox : IMAPFolder? = null
    private var store: IMAPStore? = null
    private val file = File(config.uidfile ?: "imap_uid.txt")
    private lateinit var messagesReceiver: MessagesReceiver

    override fun connect() {
        connectImapFolder()
    }

    override fun sync(messagesReceiver: MessagesReceiver, watch: Boolean) {
        this.messagesReceiver = messagesReceiver

        // Configure, which messages should be synced
        when(syncMode) {
            ImapSyncMode.sincelastsync -> {}
            ImapSyncMode.new -> file.delete()
            ImapSyncMode.all -> file.writeText("0")
        }

        // Sync existing messages
        syncExisting()

        // Stop the program if watching the folder was not requested
        if (!watch) return

        // Watch for future messages
        runImapWatcher()
    }

    private fun connectImapFolder() : IMAPFolder {
        val port = config.port ?: 993

        do {
            try {
                // Stop the old connection if necessary
                if (inbox?.isOpen == true) inbox?.close()
                inbox = null
                store?.close()
                store = null

                // Create a new connection
                Thread.sleep(5000)
                Logger.getGlobal().info("Connecting to source ${config.user}@${config.server}:${port}/${config.folder}")

                store = session.getStore("imaps") as IMAPStore
                store!!.connect(config.server, port, config.user, config.password)
                inbox = store!!.getFolder(config.folder) as IMAPFolder
                inbox!!.open(if (move) Folder.READ_WRITE else Folder.READ_ONLY)
                Thread.sleep(5000)
            } catch (e: Exception) {
                Logger.getGlobal().severe("Connecting to source ${config.user}@${config.server}:${port}/${config.folder} failed: ${e.localizedMessage}")
                Thread.sleep(5000)
            }
        } while(inbox?.isOpen != true)

        return inbox!!
    }

    private fun syncExisting(setUidNext: Long? = null) {
        if (file.exists()) {
            val lastsynceduid = file.readText().toLong()
            val nextuid = setUidNext ?: inbox!!.uidNext
            for (uid in lastsynceduid + 1 until nextuid) {
                inbox!!.getMessageByUID(uid)?.let { message ->
                    if (!message.flags.contains(Flags.Flag.DELETED)) {
                        val savedSuccessfully = messagesReceiver.saveMessage(uid, message)
                        if (savedSuccessfully && move) {
                            Logger.getGlobal().info("Deleting message \"${message.subject}\"")
                            message.setFlag(Flags.Flag.DELETED, true)
                            inbox!!.expunge(arrayOf(message))
                        }
                    }
                }
                file.writeText("$uid")
            }
        } else {
            file.writeText("${inbox!!.uidNext - 1}")
        }
    }

    fun runImapWatcher() {
        Logger.getGlobal().info("runImapWatcher start")

        val inbox = inbox!!
        // Install listener to automatically copy new messages to the target server
        addListener(inbox)

        // Install Thread that runs NOOP every five minutes to keep the connection open.
        var runIdleCommand = true;
        val watchNoopThread = WatchNoopThread(inbox) {
            // Stop the previous "idle" loop, because runImapWatcher has started a new one now
            runIdleCommand = false

            // This function is called when the NOOP thread detects a problem with the
            // IMAP connection and requests a new IMAP connection. The new IMAP connection
            // also needs to sync missed messages and needs to watch for new messages.
            val folder: IMAPFolder = connectImapFolder()

            // Sync existing messages
            syncExisting()

            // Watch for future messages
            runImapWatcher()

            folder
        }
        Thread(watchNoopThread, "IMAPConnectionKeepAlive").start()

        // Wait for new messages
        Logger.getGlobal().info("Start watching for incoming emails (last email: ${inbox.uidNext - 1})")
        while (runIdleCommand && inbox.isOpen) {
            try {
                Logger.getGlobal().warning("IDLE command");
                var t = System.currentTimeMillis()
                inbox.idle()
                if (System.currentTimeMillis() - t < 1000) {
                    Thread.sleep(1000) // Up to one IDLE per second
                }
            } catch (e: FolderClosedException) {
                e.printStackTrace()
                Thread.sleep(10 * 1000) // Wait 10 seconds, then try again
                connect()
                syncExisting()
                addListener(this.inbox!!)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Thread.sleep(10 * 1000) // Wait 10 seconds, then try again
                connect()
                syncExisting()
                addListener(this.inbox!!)
            } catch (e: IOException) {
                e.printStackTrace()
                Thread.sleep(10 * 1000) // Wait 10 seconds, then try again
                connect()
                syncExisting()
                addListener(this.inbox!!)
            }
        }

        Logger.getGlobal().warning("runImapWatcher exit");
    }

    private fun addListener(inbox: IMAPFolder) {
        inbox.addMessageCountListener(object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent?) {
                super.messagesAdded(e)

                // Copy the new messages to the target server
                e?.messages?.forEach { message ->
                    syncExisting(inbox.getUID(message) + 1)
                }
            }
        })
    }
}

/**
 * Reads all emails from a local directory that contains .eml or .eml.gz files.
 */
class LocalFolderProvider(val config: ImapConfig) : MessagesProvider {
    private val session: Session = Utils.getIMAPSSession()
    private val path = File(config.path)

    override fun connect() {
        Logger.getGlobal().info("Reading emails from folder \"${File(path, config.folder).absolutePath}\".")
    }

    override fun sync(messagesReceiver: MessagesReceiver, watch: Boolean) {
        File(path, config.folder).listFiles(FileFilter { it.name.endsWith(".eml") || it.name.endsWith(".eml.gz") }).forEach { file ->
            Logger.getGlobal().info("Reading file ${file.absolutePath}")

            var inputStream : InputStream = FileInputStream(file)
            if (file.name.endsWith(".gz")) inputStream = GZIPInputStream(inputStream)

            messagesReceiver.saveMessage(file.name.substringBefore(".").toLong(), MimeMessage(session, inputStream))
        }

        if (watch) Logger.getGlobal().info("Watch is not supported for local directories.")
    }
}
