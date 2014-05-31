package example.thread_local;

import java.util.concurrent.CountDownLatch;

/**
 * Created by Naoyuki Yoshinori on 2014/05/31.
 */
public class RequestProcessor implements Runnable {

    private CountDownLatch startLatch;
    private Action action;
    private int numberOfRequests;

    public RequestProcessor(CountDownLatch startLatch, Action action, int numberOfRequests) {
        this.startLatch = startLatch;
        this.action = action;
        this.numberOfRequests = numberOfRequests;
    }

    @Override
    public void run() {
        try {
            startLatch.await();

            for (int i = 1; i <= numberOfRequests; i++) {
                ActionForm actionForm = new ActionForm();
                actionForm.setMessage("メッセージがあります");
                action.execute(actionForm);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
