package chatapp.server;

import chatapp.client.Client;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * @author Zicheng Gao
 */
public class User extends Thread {
    private Server server;
    private Socket socket;
    private InputStreamReader streamIn;
    private OutputStreamWriter streamOut;

    private User correspondent;

    private String terminator = "\n";

    private boolean active;

    public User(Server server, Socket socket) {
        active = true;
        this.server = server;
        this.socket = socket;
        this.setName("GUEST" + socket.getInetAddress());
        // for nickname, use the thread Name field :)
    }

    // Thread run action
    @Override
    public void run() {

        StringBuilder stringBuilder = new StringBuilder(64);
        try {
            streamIn = new InputStreamReader(socket.getInputStream());
            streamOut = new OutputStreamWriter(socket.getOutputStream());
            server.log("CONNECT: @ " + socket.getInetAddress() + ":" + server.getPort());

            try {
                while (active) {
                    String message = read(stringBuilder);
                    process(message);
                }
            } catch (SocketException e) {
                handleSocketException(e);
            }

            // disconnect - inform chatapp.server to inform correspondents
            disconnect();

            socket.close();

        } catch (IOException e) {
            System.err.println("Error occurred while interacting with the socket!");
            e.printStackTrace();
        }

    }

    private void handleSocketException(SocketException e) {
        String identifier = getName() +  " @ " + socket.getInetAddress() + ":" +  socket.getPort();
        if (e.getMessage().equals("Connection reset") || e.getMessage().equals("Connection closed"))
            server.log("DISCONNECT: User " + identifier);
        else
            System.err.println("DISCONNECT-ERROR: User " + identifier);
    }

    public void disconnect() {
        active = false;
        if (getCorrespondent() != null)
            dropCorrespondent();
        // remove name from chatapp.server entries
        if (server.getNamedUsers().get(getName()) != null) {
            server.getNamedUsers().remove(getName());
        }
    }

    public void dropCorrespondent() {
        getCorrespondent().write("OK" + Client.CONTROL_DELIMITER + "listen" + Client.END_OF_HEADER +
                "SERVER: User '" + getName() + "' has disconnected.");
        getCorrespondent().setCorrespondent(null);
        setCorrespondent(null);
    }

    public String getTerminator() {
        return terminator;
    }

    public void setTerminator(String terminator) {
        this.terminator = terminator;
    }

    public User getCorrespondent() {
        return correspondent;
    }

    public void setCorrespondent(User correspondent) {
        this.correspondent = correspondent;
    }

    // Take in and flush a StringBuilder to lessen memory overhead
    private String read(StringBuilder stringBuilder) throws IOException {
        stringBuilder.delete(0, stringBuilder.length());

        // Read until termination
        int c = streamIn.read();
        stringBuilder.append((char)c);
        while (c > 0 && !stringBuilder.toString().endsWith(getTerminator())) {
             c = streamIn.read();
             stringBuilder.append((char)c);
        }

        int endPosition = stringBuilder.indexOf(getTerminator());

        if (endPosition != -1)
            stringBuilder.delete(endPosition, stringBuilder.length());

        return stringBuilder.toString();
    }

    public synchronized void write(String msg) {
        try {
            server.log("TO " + getName() + ": \"" + msg + "\"");
            streamOut.write(msg + getTerminator());
            streamOut.flush();
        } catch (SocketException e) {
            handleSocketException(e);
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the socket for user " + getName());
            e.printStackTrace();
        }
    }

    private synchronized void process(String msg) {
        server.log("FROM " + getName() + ": \"" + msg + "\"");

        if (msg.charAt(0) == Server.CTRL_HEAD)
            write(server.getActions().processAction(this, msg));
        else {// normal message
            if (getCorrespondent() != null)
                getCorrespondent().write(getName() + ": " + msg);
            else
                write("SERVER: You are not chatting with anyone.");

        }


    }

}
