package com.dgunia.imaptransfer

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The main config file in .yaml format.
 */
class YamlConfigFile {
    @JsonProperty
    var source: ImapConfig? = null

    @JsonProperty
    var target: TargetConfig? = null

    @JsonProperty
    var options: ImapOptions? = null

    @JsonProperty
    var filter: List<ImapFilter>? = null
}

/**
 * Configuration of an IMAP server (server, port, user, password, folder, [uidfile]) or a local folder (path, folder, compress, [uidfile])
 */
open class ImapConfig() {
    @JsonProperty
    var server: String? = null

    @JsonProperty
    var port: Int? = null

    @JsonProperty
    var user: String? = null

    @JsonProperty
    var password: String? = null

    @JsonProperty
    var folder: String? = null

    @JsonProperty
    var uidfile: String? = null

    @JsonProperty
    var path: String? = null

    @JsonProperty
    var compress: Boolean? = null
}

class TargetConfig : ImapConfig() {
    @JsonProperty
    var autoresponder_file: String? = null

    @JsonProperty
    var ignore_from: String? = null

    @JsonProperty
    var from: String? = null

    @JsonProperty
    var to: String? = null

    @JsonProperty
    var bcc: String? = null

    @JsonProperty
    var default_subject: String? = null

    @JsonProperty
    var smtp_host: String? = null

    @JsonProperty
    var smtp_port: Int? = null

    @JsonProperty
    var smtp_user: String? = null

    @JsonProperty
    var smtp_password: String? = null
}

/**
 * Determines if all messages, only new messages (arriving while the program is running) or all messages since the last sync (according to the UID in uidfile) are synced.
 */
enum class ImapSyncMode { all, new, sincelastsync }

/**
 * Options for the sync. "move" causes the message to be deleted from the source server after it has been copied.
 */
class ImapOptions {
    @JsonProperty
    var syncmode : ImapSyncMode? = null

    @JsonProperty
    var move = false

    @JsonProperty
    var watch = true
}

/**
 * Filters can be used to save certain messages into special folders.
 */
class ImapFilter {
    @JsonProperty
    var folder: String? = null

    @JsonProperty
    var subject: String? = null

    @JsonProperty
    var sender: String? = null

    @JsonProperty
    var receiver: String? = null
}
