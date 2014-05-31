package example.thread_local;

/**
 * Created by Naoyuki Yoshinori on 2014/05/31.
 */
public class ActionFormContext {

    private static final ThreadLocal<ActionForm> actionFormThreadLocal = new ThreadLocal<>();

    public static ActionForm getActionForm() {
        return actionFormThreadLocal.get();
    }

    public static void setActionForm(ActionForm actionForm) {
        actionFormThreadLocal.set(actionForm);
    }

    public static void removeActionForm() {
        actionFormThreadLocal.remove();
    }
}
