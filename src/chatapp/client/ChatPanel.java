package chatapp.client;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * @author Zicheng Gao
 */
public class ChatPanel extends JPanel {
    private JTextArea displayArea;
    private JTextArea entry;
    private JScrollPane displayPane;
    private Client client;

    private final static String newLine = "\n";

    public ChatPanel(Client client) {
        super(new GridBagLayout());
        this.client = client;
        this.setSize(600, 400);

        displayArea = new JTextArea("Initialized chatapp.client.\n", 20, 40);
        displayArea.setEditable(false);
        displayPane = new JScrollPane(displayArea);

        // We have to use a textArea because textField doesn't like newLine
        entry = new JTextArea(3,40);
        entry.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    // Pressing enter should still be the thing...
                    if (entry.getText().endsWith(newLine))
                        process(entry.getText());
                });
            }

            @Override
            public void removeUpdate(DocumentEvent e) {}

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        GridBagConstraints c = new GridBagConstraints();

        // Pane to top
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridy = 0;
        c.weightx = 1.0;
        c.weighty = 1.0;
        add(displayPane, c);

        // Entry to bottom
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0.0;
        c.gridy = 1;
        add(entry, c);

        displayPane.setPreferredSize(new Dimension(640, 480));
        displayPane.setMinimumSize(new Dimension(320, 240));
        entry.requestFocusInWindow();
    }

    public void process(String msg) {
        // TODO - what about multiline? don't cut our own messages up.
        // Either don't print to textbox until it's "All done" or...
        // Use a textArea for the text entry in actual?

        if (!client.isConnected()) {
            write("ERROR: You are not connected to any server.");
            entry.setText("");
            return;
        }

        // make sure there is any input
        if (msg.length() > 0) {
            // Check for delimiter...
            int endPos = msg.indexOf(client.getTerminator());

            // Have delimiter, will (process) and send
            if (endPos >= 0) {
                // TODO - a multiline username would be allowed by the functionality definition.
                // this should be prevented if the project is continued beyond the class specs

                // Remove delimiter for processing, prevent empty messages
                msg = msg.substring(0, endPos);
                if (msg.length() < 1)
                    return;

                // Even if we change the terminator, the server won't know about the new one until post-change
                // So we need to send the message that changes the terminator with the old one
                String oldTerminator = client.getTerminator();

                String[] args = msg.split(" ");

                // nick change goes into chatapp.client.pendingChanges for server confirmation
                if (args[0].equals("/nick") && args.length > 1) {
                    String newName = msg.substring(args[0].length() + 1); // nick only
                    client.getPendingChanges().put("nick", new String[]{newName});
                } else if (args[0].equals("/delimit") && args.length > 1) {
                    String newTerminator = msg.substring(args[0].length() + 1); // We included the / here
                    client.setTerminator(newTerminator);
                } else if (args[0].equals("/msg") && args.length > 1)
                    client.getPendingChanges().put("msg", null);

                client.send(msg + oldTerminator);

                if (args[0].charAt(0) != '/')
                    write(client.getUsername() + ": " + msg);

                entry.setText("");
            } else
                entry.setText(msg);
        }
    }

    public synchronized void clear() {
        displayArea.setText("");
    }

    // Write to display box
    public synchronized void write(String msg) {
        // For convenience and readability, indent non-ending newlines
//        msg = msg.replaceAll("\n(?!$)","\n\t");
        // in practice, the tab width made everything rather a bit inconsistent. we thus do not indent
        // TODO in further practice we might want to have a two-column, resizing situation. username on left,
        // texts on right. this way we can ensure rightful justification

        // Further chatapp.client-side readability
        if (!msg.endsWith(newLine))
            msg += newLine;

        displayArea.append(msg);
        displayArea.setCaretPosition(displayArea.getDocument().getLength());
    }

}
