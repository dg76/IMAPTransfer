# IMAPTransfer

It can be used to copy, move and forward emails from one server to another.

### Configuration

The easiest configuration is to use a .yaml file like this:

```
source:
  server: my1.mailserver.com
  port: 993
  user: user1
  password: password1
  folder: INBOX
target:
  server: my2.mailserver.com
  port: 993
  user: user2
  password: password2
  folder: INBOX
options:
  syncmode: all
  move: false
  watch: false
```

and then start the program using

`java -jar IMAPTransfer.jar -c config.yaml`

This example would copy all emails in the folder INBOX from server my1.mailserver.com to server m2.mailserver.com.

### Moving emails

If you want to move all emails instead, just set `move: true` in the file above. Then the emails on the source server will be deleted after they have been copied.

### Forwarding emails

The program can stay connected to your IMAP servers and
immediately forward all incoming emails from the source server
to the target server. It is similar to using a normal email forward
(e.g. a `.forward` file) but fixes a problem:

Let's say a user from mail1.com sends you an email to my1.mailserver.com .
So the domain of the sender is mail1.com . Now your server my1.mailserver.com
forwards your email to my2.mailserver.com e.g. using a `.forward` file. That
means it will open an SMTP connection to my2.mailserver.com and send
mail1.com's email. But there is a problem. If mail1.com has an [SPF](https://en.wikipedia.org/wiki/Sender_Policy_Framework) DNS entry
saying "-all", my2.mailserver.com will refuse the email because my2.mailserver.com
is not an official mailserver for the domain mail1.com . This will result
in an error message being sent back to mail1.com telling him that the
email could not be delivered.

The solution is to use IMAP to forward the email, e.g. using this IMAPTransfer program.

Please be aware that only the spam filter of my1.mailserver.com will be used. When
forwarding the email via SMTP also the spam filter of my2.mailserver.com would be used but
when using IMAP, the email won't be checked again by the spam filter on my2.mailserver.com.

To use it for forwarding emails you will probably use these settings in your .yaml file:

```
options:
  syncmode: sincelastsync
  move: false
  watch: true
```

And if you don't want to keep a copy of the emails on the source server, just set `move` to `true`.

`syncmode` can be `all`, `new` or `sincelastsync`:
- `all` all past and future emails of the folder will be copied/moved
- `new` all existing emails will be ignored and the folder will be watched for new emails, which will then be copied/moved/forwarded
- `sincelastsync` reads the last sync position from the file "imap_uid.txt" (or as configured in the `uidfile` option) and start the sync from there. This way it can continue a sync when it was stopped.

`watch` makes the program stay connected to the IMAP server and watch for new emails. Otherwise, it would
exit after it finishes processing the existing emails.

### Archiving emails

By setting a local target instead of an IMAP server you can save the emails into local eml files instead:

```
source:
  server: my1.mailserver.com
  port: 993
  user: user1
  password: password1
  folder: INBOX
target:
  path: myarchive
  folder: INBOX
  compress: true
```

This will save the emails into separate files e.g. `myarchive/INBOX/55.eml` . Using `compress` the
emails can be gzip-compressed and are saved e.g. into `55.eml.gz`.

### Restoring emails

To restore the emails from such a directory just write the opposite configuration:

```
source:
  path: myarchive
  folder: INBOX
target:
  server: my2.mailserver.com
  port: 993
  user: user2
  password: password2
  folder: INBOX
```

This will write them back from the local files into the IMAP server. Please be aware that it will
process all emails in `myarchive/INBOX`, i.e. the `syncmode`, `watch` and `move` parameters are ignored.
It will read `.eml` and `.eml.gz` files.

### Filter

Maybe you want to copy/move/forward the emails into different folders on the
target server. For this purpose there is a "filter" section in the .yaml file:

```
filter:
  - folder: Mail delivery failed
    subject: ^Mail delivery failed.*
```

This filter would copy/move/forward all emails with a subject starting with
"Mail delivery failed." into a folder named "Mail delivery failed" on the
target server. All other emails will be saved into the default folder
configured in the "target" section of the .yaml file.

The filter can check the subject, sender and receiver of an email:
```
filter:
  - folder: Mail delivery failed
    subject: ^Mail delivery failed.*
    sender: .*@example.com.*
    receiver: .*@myserver.com.*
```

### Building

The jar file can be built using

```
mvn clean compile assembly:single
```

It can be found in `target/IMAPTransfer-0.5-jar-with-dependencies.jar` afterward.
