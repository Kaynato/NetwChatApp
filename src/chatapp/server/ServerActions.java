package chatapp.server;

import java.util.HashMap;
import java.util.function.BiFunction;

/**
 * Functionally, for each string name of command
 * associates a function that takes in message parameters, {@link Server}, {@link User}
 * and returns a string.
 *
 * Function argument 0 is the name of the action itself, while 1 and on refer to the passed in arguments
 * @author Zicheng Gao
 */
public class ServerActions extends HashMap<String, BiFunction<String, String[], BiFunction<Server, User, String>>>{
    private Server server;

    public ServerActions(Server server) {
        this.server = server;
    }

    // Take in a control message
    public String processAction(User user, String cMsg) {
        String[] msgArgs = cMsg.split(" ");
        String actionName = msgArgs[0].substring(1); // Turn "/nick " into "nick"
        msgArgs[0] = actionName;

        // Find action
        BiFunction<String, String[], BiFunction<Server, User, String>> action = get(actionName);

        if (action != null)
            return action.apply(cMsg, msgArgs).apply(server, user);
        else
            return "Control message \"" + actionName + "\" was not recognized.";

    }

}
