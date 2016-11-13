package chatapp.client;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * @author Zicheng Gao
 * Client application for chat application
 */
public class Client {

    // Confirm successful control message that should reflect to the client
    // No one should be able to manually send these
    // Incoming receipts should be of the format confirmation-controlType-startOfText-MESSAGE
    // For example, "OK\u0001nick\u0002Name successfully set to Name1"
    // Or, "NO\u0001nick\u0002Nick Name1 already exists."
    public static final String CONTROL_DELIMITER = "\u0001";
    public static final String END_OF_HEADER = "\u0002";

    private String username;
    private String terminator;

    private JFrame frame;

    private HashMap<String, String[]> pendingChanges;

    private ChatPanel chatPanel;

    private Socket socket;
    private InputStreamReader streamIn;
    private OutputStreamWriter streamOut;
    private boolean connected;

    private Thread receiverThread;

    public Client() {
        terminator = "\n";
        username = "UNNAMED_GUEST";
        pendingChanges = new HashMap<>(8);
        if (connected = connect()) {
            // Create window
            frame = makeGUI();

            // Initialize as listener
            getPendingChanges().put("msg", null);

            // Connect streamIn stream to ChatPanel TextArea.
            // Display incoming messages.
            receiverThread = new ReceiverThread();
            receiverThread.start();

            // Text entry listener dealt with by ChatPanel

        } else
            System.err.println("Could not successfully open a connection.");
    }

    private boolean connect() {
        // Ask for hostname
        String hostname = JOptionPane.showInputDialog("Enter the hostname of the server:");
        if (hostname == null)
            return false;

        // Ask for port number
        int port = -1;
        while (port < 0) {
            try {
                String portString = JOptionPane.showInputDialog("Enter the port of the server:");
                if (portString == null)
                    return false;
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                System.err.println("Please enter a valid port number.");
            }
        }

        // Create socket and streams
        try {
            socket = new Socket(hostname, port);
            streamIn = new InputStreamReader(socket.getInputStream());
            streamOut = new OutputStreamWriter(socket.getOutputStream());
        } catch (UnknownHostException e) {
            System.err.println("Could not identify the host.");
            return false;
        } catch (SocketException e) {
            System.err.println("Socket encountered an error when trying to connect.");
            return false;
        } catch (IOException e) {
            System.err.println("An error occurred while creating the socket!");
            return false;
        }

        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        connected = false;
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("An error occurred while closing the connection.");
        }
    }

    /**
     * Send a message to the server.
     * @param msg message to send
     */
    public void send(String msg) {
        try {
            streamOut.write(msg);
            streamOut.flush();
        } catch (IOException e) {
            System.err.println("An error occurred while sending data.");
        }
    }

    private JFrame makeGUI() {
        JFrame window = new JFrame("Chat-app Client");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        chatPanel = new ChatPanel(this);

        window.add(chatPanel);
        window.pack();
        window.setLocationRelativeTo(null); // Center window
        window.setVisible(true);
        return window;
    }

    public String getTerminator() {
        return terminator;
    }

    public void setTerminator(String terminator) {
        this.terminator = terminator;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public HashMap<String, String[]> getPendingChanges() {
        return pendingChanges;
    }

    /**
     * Handles received messages
     */
    private class ReceiverThread extends Thread {

        private boolean hasOption(String option, String[] args) {
            return getPendingChanges().containsKey(option) && args.length > 1 && args[1].equals(option);
        }

        @Override
        public void run() {
            int c;
            StringBuilder stringBuilder = new StringBuilder(64);
            try {
                while (isConnected()) {
                    stringBuilder.delete(0, stringBuilder.length());

                    c = streamIn.read();
                    stringBuilder.append((char) c);
                    while (c > 0 && !stringBuilder.toString().endsWith(getTerminator())) {
                        c = streamIn.read();
                        stringBuilder.append((char) c);
                    }

                    // Process receipt of successful changes: e.g. delimiter changes, such
                    // If there is a header message - (we only care for things that need receipt, such as
                    //  starting a chat, or changing nickname)
                    int headerEnd = stringBuilder.indexOf(END_OF_HEADER);

                    if (headerEnd > 0) {
                        String[] args = stringBuilder.substring(0, headerEnd).split(CONTROL_DELIMITER);

                        // Check to make sure the receipt is for the requested action
                        if (args[0].equals("OK")) { // action confirmed
                            // confirm nick change
                            if (hasOption("nick", args) && getPendingChanges().get("nick")[0].equals(args[2])) {
                                setUsername(getPendingChanges().get("nick")[0]);
                                getPendingChanges().remove("nick");
                            }
                            // if we are listening, we should clear the area if we are starting a new chat
                            if (hasOption("msg", args)) {
                                // clear on receipt
                                chatPanel.clear();
                                getPendingChanges().remove("msg");
                            }

                            // Server tells us to become a listener, as our partner has left
                            if (args.length > 1 && args[1].equals("listen"))
                                getPendingChanges().put("msg", null);

                            if (args.length > 1 && args[1].equals("quit"))
                                disconnect();

                        } else if (args[0].equals("NO") && args.length > 0 && getPendingChanges().containsKey(args[1]))
                                getPendingChanges().remove(args[1]); // Reject pending change on failure

                        // Detach header
                        stringBuilder.delete(0, headerEnd + 1);
                    }

                    // Vanish terminator. server sends with terminator of recipient
                    if (stringBuilder.toString().endsWith(getTerminator()))
                        stringBuilder.delete(stringBuilder.lastIndexOf(getTerminator()), stringBuilder.length());

                    chatPanel.write(stringBuilder.toString());
                }
            } catch (IOException e) {
                System.err.println("The connection to the server was interrupted.");
            }
        }

    }

    public static void main(String[] args) {
        Client client = new Client();
    }

}