package example.thread_local;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Naoyuki Yoshinori on 2014/05/31.
 */
public class Main {

    // スレッド数
    private static final int NUMBER_OF_THREADS = 100;
    // リクエスト数
    private static final int NUMBER_OF_REQUESTS = 10;

    public static void main(String[] args) {
        testThreadSafe(new ThreadUnsafeAction());
        testThreadSafe(new ThreadSafeAction());
    }

    public static void testThreadSafe(Action action) {
        String className = action.getClass().getSimpleName();
        System.out.println(className + " start.");

        CountDownLatch startLatch = new CountDownLatch(1);
        Collection<Thread> threads = new HashSet<>();

        // 複数のスレッドでアクションを共有する。
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            Thread thread = new Thread(new RequestProcessor(startLatch, action, NUMBER_OF_REQUESTS));
            thread.start();
            threads.add(thread);
        }

        // 同時にスレッドを実行する。
        startLatch.countDown();
        try {
            // スレッドが終了するのを待つ
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(className + " end.");
    }
}
