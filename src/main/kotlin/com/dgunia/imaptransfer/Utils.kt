package com.dgunia.imaptransfer

import javax.mail.Session

object Utils {
    fun getIMAPSSession(): Session {
        val props = System.getProperties()
        props.setProperty("mail.store.protocol", "imaps")

        props.put("mail.imap.timeout", "30000")
        props.put("mail.imaps.timeout", "30000")
        props.put("mail.gimaps.timeout", "30000")
        props.put("mail.imap.starttls.enable", true)

        return Session.getInstance(props, null)
    }
}