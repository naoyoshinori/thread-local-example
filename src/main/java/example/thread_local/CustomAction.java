package example.thread_local;

/**
 * Created by Naoyuki Yoshinori on 2014/05/31.
 */
public class CustomAction extends Action {

    protected ActionForm unsafeActionForm;

    @Override
    public void execute(ActionForm safeActionForm) {
        // クラス変数とインスタンス変数はスレッドセーフでない。
        this.unsafeActionForm = safeActionForm;

        try {
            // スレッドローカルを使用することで、スレッドセーフを実現。
            ActionFormContext.setActionForm(safeActionForm);

            // 処理
            executeLogic();

        } finally {
            // スレッドローカルで確保したオブジェクトはスレッド終了時に破棄されるが、
            // Web アプリケーションのようにスレッドを共有する場合は、
            // メモリリークが起きないように明示的に開放する必要がある。
            ActionFormContext.removeActionForm();
        }
    }

    public void executeLogic() {
    }
}
