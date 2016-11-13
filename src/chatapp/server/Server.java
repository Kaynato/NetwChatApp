package chatapp.server;

import chatapp.client.Client;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;

/**
 * @author Zicheng Gao
 */
public class Server {
    // Each 'header' field is one char
    public static final char CTRL_HEAD = '/';
    public static final char MSG_HEAD = 'm';

    private ServerSocket welcomeSocket = null;
    private int port;

    // Nickname -> Socket
    private HashMap<String, User> namedUsers = null;

    // Extensible!
    private ServerActions actions;

    public static final String NAME = "nick";
    public static final String TALKTO = "msg";
    public static final String DELIMIT = "setend";
    public static final String EXIT = "quit";

    public Server(int port) {
        this.port = port;
        this.namedUsers = new HashMap<String, User>(64);
        try {
            welcomeSocket = new ServerSocket(port);
            actions = new ServerActions(this);

            // thank goodness for java 8
            // Change nick
            actions.put("nick", (msg, args) -> (server, user) -> {
                if (args.length < 2)
                    return "NO" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER +
                            "SERVER: Invalid use of /nick. Please specify a new name.";

                String oldName = user.getName();
                String newName = msg.substring(args[0].length() + 2); // +2 for / and space

                // reserved name?
                // TODO - force chatapp.client to "previous name" ?
                if (newName.equals("Listener"))
                    return "NO" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER + "SERVER: 'Listener' is a reserved name.";

                // name available?
                if (namedUsers.containsKey(newName))
                    return "NO" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER +
                            "SERVER: " + newName + " already in use."; // TODO - force chatapp.client to "previous name" ?

                // get rid of old name if it's there
                if (namedUsers.containsKey(oldName))
                    namedUsers.remove(oldName);

                // if available, set name and add to "named users"
                user.setName(newName);
                namedUsers.put(newName, user);
                return "OK" + Client.CONTROL_DELIMITER + args[0] + Client.CONTROL_DELIMITER + newName
                        + Client.END_OF_HEADER + "SERVER: Name successfully set to " + newName;
            });

            // Talk to
            actions.put("msg", (msg, args) -> (server, user) -> {
                // invalid use
                if (args.length < 2)
                    return "SERVER: Invalid use of /msg. Please specify a target user or 'Listener.'";

                String targetName = msg.substring(args[0].length() + 2); // +2 for / and space
                // make sure we have a name first
                if (!namedUsers.containsKey(user.getName()))
                    return "SERVER: You are not a named user. Use /nick to set your name before chatting.";

                // If we want to become a listener / end and existing conversation
                if (targetName.equals("Listener")) {
                    if (user.getCorrespondent() != null) {
                        user.dropCorrespondent();
                        return "OK" + Client.CONTROL_DELIMITER + "listen" + Client.END_OF_HEADER +
                                "SERVER: Ended chat and now listening.";
                    } else {
                        // already al istener
                        return "NO" + Client.CONTROL_DELIMITER + "listen" + Client.END_OF_HEADER +
                                "SERVER: You are already a listener.";
                    }
                }

                // check for our target
                if (!namedUsers.containsKey(targetName))
                    return "SERVER: User \"" + targetName + "\" is not online.";
                User target = namedUsers.get(targetName);

                // target is not chatting and is different
                // (target is a listener)
                if (target.getCorrespondent() != null) {
                    if (target.getCorrespondent() != user)
                        return "SERVER: User \"" + targetName + "\" is already in a chat.";
                    else
                        return "SERVER: You are already chatting with " + targetName;
                }

                // inform old correspondent, if any, of a switch
                if (user.getCorrespondent() != null) {
                    user.getCorrespondent().write("OK" + Client.CONTROL_DELIMITER + "listen" + Client.END_OF_HEADER +
                            "SERVER: User \"" + user.getName() + "\" is no longer chatting.");
                    user.getCorrespondent().setCorrespondent(null);
                }

                // found - please set
                target.setCorrespondent(user);
                user.setCorrespondent(target);
                target.write("OK" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER +
                        "SERVER: Now chatting with " + user.getName());

                return "OK" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER +
                        "SERVER: Now chatting with " + targetName;
            });

            // Change Delimiter
            actions.put("delimit", (msg, args) -> (server, user) -> {
                // change terminator
                if (args.length > 1) {
                    String newTerminator = msg.substring(args[0].length() + 2); // +2 for / and space
                    user.setTerminator(newTerminator);
                    // Displayability?? Newlines make for awful messages
                    if (newTerminator.equals("\n"))
                        newTerminator = "Enter";
                    if (newTerminator.endsWith("\n"))
                        newTerminator = newTerminator.substring(0, newTerminator.length() - 1);
                    // Do not return something containing the delimiter.
                    // The chatapp.client chops up the message at the wrong part.
//                    return "SERVER: User " + user.getName() + " has changed delimiter to <" + newTerminator + ">";
                    return "SERVER: Delimiter changed.";
                } else {
                    return "SERVER: No delimiter specified!";
                }
            });

            // Quit server
            actions.put("quit", (msg, args) -> (server, user) -> {
                String output = "OK" + Client.CONTROL_DELIMITER + args[0] + Client.END_OF_HEADER +
                        "User " + user.getName() + " has quit";
                user.disconnect();
                if (args.length > 1)
                    return output + " with message \"" + args[1] + "\"";
                else
                    return output + ".";
            });

        } catch (IOException e) {
            System.err.println("An error occurred while setting up the server socket!");
            e.printStackTrace();
        }
    }

    public int getPort() {
        return port;
    }

    public ServerActions getActions() {
        return actions;
    }

    public HashMap<String, User> getNamedUsers() {
        return namedUsers;
    }

    public void close() {
        try {
            welcomeSocket.close();
        } catch (IOException e) {
            System.err.println("An error occurred while closing the server socket!");
            e.printStackTrace();
        }
    }

    public void log(String s) {
        // encapsulation. TODO - will change?
        System.out.println(s);
    }

    public static void main(String[] args) {
        int port;
        if (args.length > 1 && args[0].equals("-port")) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number.");
                return;
            }
        } else {
            System.err.println("No port option specified.");
            return;
        }

        Server server = new Server(port); // In this case, should be 5000 + 10

        try {
            System.out.println("The server is running on port " + server.getPort());
            while (true)
                try {
                    new User(server, server.welcomeSocket.accept()).start();
                } catch (IOException e) {
                    System.err.println("An error occurred while establishing a connection!");
                    e.printStackTrace();
                }
        } finally {
            server.close();
        }
    }

}
