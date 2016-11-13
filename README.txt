Zicheng Gao - EECS 325 (USING 425 SPECS)

Port: 5010

Commands:

    /nick [NEW-NICKNAME]
    This changes the nickname of the chatapp.client, if the new nickname is available.

    /msg [TARGET]
    Attempts to begin a chat with the target.
    If "Listener" is used instead, this will terminate any chat currently in progress.
    Clients become Listeners on initialization and exiting chats, so there is no need to type this repeatedly.

    /delimit [NEW-DELIMITER]
    Changes delimiter.

    /quit
    Causes the chatapp.client to quit.
    This does not shut down the application (though it could).
    Messages will then not send after this point.

Strange Behaviors:

    The system is sensitive to whitespace, so an unsightly behavior may arise when including whitespace in names or delimiters.

    Newlines are appended when pressing enter, but as enter must be pressed at all to send messages, all delimiters will inherently include a newline.
    For example, even if you type "/delimit AND" what is truly there when sending is "/delimit AND\n"
    Perhaps this is non-problematic, as pressing enter implicitly sends anyway, after a delimiter, so all messages would have to end with a newline.

