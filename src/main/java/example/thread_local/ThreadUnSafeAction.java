package example.thread_local;

/**
 * Created by Naoyuki Yoshinori on 2014/05/31.
 */
public class ThreadUnsafeAction extends CustomAction {

    public static final String MESSAGE = "メッセージを上書しました";

    @Override
    public void executeLogic() {
        unsafeActionForm.setMessage(MESSAGE);

        String message = unsafeActionForm.getMessage();
        if (!MESSAGE.equals(message)) {
            long id = Thread.currentThread().getId();
            System.out.println(String.format(
                    "エラー: %4d, %s, %s, 上書きに失敗しました",
                    id, message, MESSAGE
            ));
        }
    }
}
