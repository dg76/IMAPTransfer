package com.dgunia.imaptransfer

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import java.io.*
import java.util.logging.Logger
import java.util.zip.GZIPOutputStream
import javax.mail.*
import javax.mail.internet.MimeMessage

/**
 * The emails from the MessagesProvider are sent to a MessagesReceiver that will then save these emails.
 */
abstract class MessagesReceiver {
    /**
     * Connects to the server. Must be called before the MessagesReceiver sends any messages.
     */
    abstract fun connect()

    /**
     * Called by the MessagesProvider to save a message.
     */
    abstract fun saveMessage(uid: Long, message: Message): Boolean

    /**
     * Determines the (IMAP)folder into which a message should be saved. Either the default target folder or
     * a folder set by one of the filters.
     */
    protected fun getTargetFolderName(config: ImapConfig, filter: List<ImapFilter>?, newMessage: Message): String {
        var targetFolder = config.folder!!

        // ggf. anderen Zielordner nehmen
        filter?.forEach {
            if (it.subject == null || newMessage.subject.matches(Regex(it.subject!!))) {
                if (it.sender == null || newMessage.from.any { sender -> sender.toString().matches(Regex(it.sender!!)) }) {
                    if (it.receiver == null || newMessage.allRecipients.any { recipient -> recipient.toString().matches(Regex(it.receiver!!)) }) {
                        targetFolder = it.folder!!
                    }
                }
            }
        }
        return targetFolder
    }
}

/**
 * Saves the emails into an IMAP account.
 */
class ImapReceiver(val config: ImapConfig, val filter: List<ImapFilter>?) : MessagesReceiver() {
    private val session: Session = Utils.getIMAPSSession()
    private lateinit var store: IMAPStore
    private val folders: MutableMap<String, IMAPFolder> = HashMap()

    override fun connect() {
        val port = config.port ?: 993
        Logger.getGlobal().info("Connecting to target ${config.user}@${config.server}:${port}/${config.folder}")
        store = session.getStore("imaps") as IMAPStore
        store.connect(config.server, port, config.user, config.password)
        folders.clear()
        folders[config.folder!!] = getTargetFolderWithName(config.folder!!)
    }

    override fun saveMessage(uid: Long, message: Message): Boolean {
        return copyMessageToTarget(session, message)
    }

    /**
     * Copies the message "message" from sourceFolder onto the target server. The target folder is determined
     * using the filter rules by using the #getTargetFolderForMessage method.
     *
     * @return true if successful
     */
    private fun copyMessageToTarget(session: Session, message: Message): Boolean {
        val byteArrayOutputStream = ByteArrayOutputStream()
        message.writeTo(byteArrayOutputStream)
        val newMessage = MimeMessage(session, ByteArrayInputStream(byteArrayOutputStream.toByteArray()))

        Logger.getGlobal().info("Writing \"${newMessage.subject}\" into folder \"${getTargetFolderForMessage(newMessage).name}\"")

        try {
            getTargetFolderForMessage(newMessage).appendMessages(arrayOf(newMessage))
            return true
        } catch (e: FolderClosedException) {
            e.printStackTrace()
            connect()
            try {
                getTargetFolderForMessage(newMessage).appendMessages(arrayOf(newMessage))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            connect()
            try {
                getTargetFolderForMessage(newMessage).appendMessages(arrayOf(newMessage))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Thread.sleep(10 * 1000) // 10 Sekunden warten, dann erneut versuchen
            connect()
            try {
                getTargetFolderForMessage(newMessage).appendMessages(arrayOf(newMessage))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch(e: MessagingException) {
            e.printStackTrace()
            connect()
            val errorMessage = MimeMessage(session)
            errorMessage.setFrom(config.user)
            errorMessage.setSubject("IMAPTransfer error: ${e.localizedMessage} for message: ${newMessage.subject}")
            errorMessage.setContent("An error ocurred when trying to copy a message.\nError: ${e.localizedMessage}\nSubject: ${newMessage.subject}", "text/plain")
            try {
                getTargetFolderForMessage(errorMessage).appendMessages(arrayOf(errorMessage))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return false
    }

    private fun getTargetFolderForMessage(newMessage: MimeMessage): IMAPFolder {
        return getTargetFolderWithName(getTargetFolderName(config, filter, newMessage))
    }

    /**
     * @return Retrieves or creates the folder "folderName" from/on the target server.
     */
    private fun getTargetFolderWithName(folderName: String): IMAPFolder {
        var folder = this.folders[folderName]
        if (folder == null) {
            folder = store.getFolder(folderName) as IMAPFolder
            if (!folder.exists()) {
                Logger.getGlobal().info("Creating IMAP folder \"${folderName}\" on server ${config.server}.")
                folder.create(Folder.HOLDS_MESSAGES)
            }
            folder.open(Folder.READ_WRITE)
            this.folders[folderName] = folder
        }
        return folder
    }
}

/**
 * Saves emails into a local folder and optionally gzip-compresses them.
 */
class LocalFolderReceiver(val config: ImapConfig, val filter: List<ImapFilter>?) : MessagesReceiver() {
    val path = File(config.path)

    override fun connect() {
        path.mkdirs()
        Logger.getGlobal().info("Writing emails into folder \"${File(path, config.folder).path}\".")
    }

    override fun saveMessage(uid: Long, message: Message): Boolean {
        val fileFolder = File(path, getTargetFolderName(config, filter, message))
        fileFolder.mkdirs()
        var outputFile = File(fileFolder, "$uid.eml")
        val fileOutputStream : OutputStream
        if (config.compress ?: false) {
            outputFile = File(outputFile.parentFile, "${outputFile.name}.gz")
            fileOutputStream = GZIPOutputStream(FileOutputStream(outputFile))
        } else {
            fileOutputStream = FileOutputStream(outputFile)
        }
        message.writeTo(fileOutputStream)
        fileOutputStream.close()
        Logger.getGlobal().info("Writing \"${message.subject}\" into file \"${outputFile.path}\"")
        return true
    }
}
