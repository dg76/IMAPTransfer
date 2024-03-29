package com.dgunia.imaptransfer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.commons.cli.*
import java.io.File

val ARG_HELP = "help"
val ARG_YAML = "config"

fun main(args: Array<String>) {
    // Shorter log output
    System.setProperty("java.util.logging.SimpleFormatter.format", "[%1\$tF %1\$tT] [%4$-7s] %5\$s %n")

    // Read command line parameters
    val options = Options()
    options.addOption(Option.builder("c").longOpt("config").desc("Config file in yaml format").hasArg().argName(ARG_YAML).build())
    options.addOption(Option.builder("h").longOpt("help").desc("Help").argName(ARG_HELP).build())

    val parser = DefaultParser()
    try {
        val cmd = parser.parse(options, args)

        if (cmd.hasOption(ARG_HELP)) {
            showHelp(options)
            return
        }

        if (cmd.hasOption(ARG_YAML)) {
            val objectMapper = ObjectMapper(YAMLFactory()).apply {
                disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
            }
            val yamlFile = File(cmd.getOptionValue(ARG_YAML))
            val yaml = objectMapper.readValue(yamlFile, YamlConfigFile::class.java)

            // Create the source messages provider
            val source: MessagesProvider
            if (yaml.source?.path != null) {
                source = LocalFolderProvider(config = yaml.source!!)
            } else {
                source = ImapProvider(config = yaml.source!!, move = yaml.options?.move ?: false, yaml.options?.syncmode ?: ImapSyncMode.new)
            }

            // Create the target messages receiver
            val target: MessagesReceiver
            if (yaml.target?.path != null) {
                target = LocalFolderReceiver(config = yaml.target!!, yaml.filter)
            } else if (yaml.target?.autoresponder_file != null) {
                val yamltarget = yaml.target!!
                target = AutoResponderReceiver(
                    autoresponderFile = File(yamlFile.parentFile, yamltarget.autoresponder_file!!),
                    ignoreFrom = yamltarget.ignore_from ?: "",
                    from = yamltarget.from ?: "",
                    to = yamltarget.to ?: "",
                    bcc = yamltarget.bcc ?: "",
                    defaultSubject = yamltarget.default_subject ?: "",
                    smtpHost = yamltarget.smtp_host ?: "",
                    smtpPort = yamltarget.smtp_port ?: 587,
                    smtpUser = yamltarget.smtp_user ?: "",
                    smtpPassword = yamltarget.smtp_password ?: ""
                )
            } else {
                target = ImapReceiver(config = yaml.target!!, yaml.filter)
            }

            // Run the sync
            ImapUtil(
                source = source,
                target = target,
                watch = yaml.options?.watch ?: true,
            ).connect()
        } else {
            showHelp(options)
        }
    } catch (e: MissingOptionException) {
        showHelp(options)
    }
}

private fun showHelp(options: Options) {
    HelpFormatter().printHelp("java ImapUtil", options)
}

class ImapUtil(val source: MessagesProvider, val target: MessagesReceiver, val watch: Boolean = true) {
    fun connect() {
        // Connect source IMAP folder
        source.connect()

        // Connect target IMAP folder
        target.connect()

        // Run the sync
        source.sync(target, watch)
    }
}

