# JavaのThreadLocalとスレッドセーフについて

## はじめに
Struts でシステムを開発していたときに、Actionで格納した値がJSPに反映されない問題が発生しました。この問題を調査した結果、Action#exceuteメソッドの引数をActionのインスタンス変数に格納して使用していたことが原因でした。ことのきに問題となったソースコードと解決方法を解説します。サンプルコードは[こちら](https://github.com/yoshi-naoyuki/thread-local-example)です。

## マルチスレッドとスレッドセーフ

スレッドセーフとはアプリケーションをマルチスレッドで動作（複数のスレッドが同時並行的に実行）しても問題がないことを指します。スレッドセーフでない場合は、あるスレッドで変更した共有データが、他のスレッドによって上書きされてしまう可能性があります。Webサーバーやデータベースなどのサーバー用ソフトウェアは、マルチスレッド（マルチプロセス）で動作しているので、サーバー向けアプリケーションを開発するときは、マルチスレッドで動作するように実装することが望ましいです。

## Javaではローカル変数のみスレッドセーフ

Javaのメモリ領域には大きく分けて、スタック領域とヒープ領域の２種類があります。スタック領域はスレッド毎に用意され、スタック領域のデータは他のスレッドの影響を受けません。逆にヒープ領域のデータは複数のスレッドに共有される可能性があり、アクセスする順序によっては意図しない動作をします。ローカル変数はスタック領域で管理されるためスレッドセーフです。クラス変数とインスタンス変数はヒープ領域で管理されるためスレッドセーフではありません。

|変数|内容|メモリ領域|スレッドセーフ|
|----|----|----------|------------|
|クラス変数|クラスで共用される変数|ヒープ領域|スレッドセーフでない|
|インスタンス変数|クラスから生成されたオブジェクト|ヒープ領域|スレッドセーフでない|
|ローカル変数|メソッドやブロック内で使われる変数|スタック領域|スレッドセーフ|

## スレッドセーフでないAction

次のクラス図を実装して、Webアプリケーション内で起きていることを再現してみました。

![ThreadSafe.png](https://qiita-image-store.s3.amazonaws.com/0/33079/1e6d722f-781e-cdfb-4982-f40cf17da8c3.png "ThreadSafe.png")

次のようなActionFormがあります。

```java
public class ActionForm {

    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
```

CustomActionクラスは、ActionFromオブジェクトをローカル変数（スタック領域）からインスタンス変数（ヒープ領域）に格納しています。

```java
public class CustomAction extends Action {

    protected ActionForm unsafeActionForm;

    @Override
    public void execute(ActionForm safeActionForm) {
        // インスタンス変数はスレッドセーフでない。
        this.unsafeActionForm = safeActionForm;

        // 処理
        executeLogic();
    }

    public void executeLogic() {
    }
}
```

ThreadUnsafeActionクラスは、unsafeActionFormオブジェクト（インスタンス変数）のメッセージを上書きしています。ソースコード上では、条件分岐のところでfalseが返されて、エラーメッセージを出力しないのですが、サンプルコードを実行するとコンソールにエラーメッセージが出力されます。

```java
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
```

RequestProcessorクラスは、Webアプリケーションと同じように、リクエスト毎にActionFormオブジェクトを生成しています。

```java
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
```

Main#testThreadSafeメソッドは、複数のスレッドで１つのThreadUnsafeActionオブジェクトを共有しています。

```java
public class Main {

    // スレッド数
    private static final int NUMBER_OF_THREADS = 100;
    // リクエスト数
    private static final int NUMBER_OF_REQUESTS = 10;

    public static void main(String[] args) {
        testThreadSafe(new ThreadUnsafeAction());
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
            // スレッドが終了するのを待つ。
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(className + " end.");
    }
}
```

最後にMain#mainを実行するとコンソール上に複数のエラーメッセージが出力されます。エラーメッセージが表示されない場合は、スレッド数やリクエスト数を増やしたり、複数回実行するとエラーメッセージが出力されます。

```
ThreadUnsafeAction start.
エラー:   36, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   96, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:  105, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   44, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   31, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   68, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   52, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   27, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   22, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:  100, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   81, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   47, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:   45, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
エラー:  102, メッセージがあります, メッセージを上書しました, 上書きに失敗しました
ThreadUnsafeAction end.
```

## スレッドセーフのAction

ヒープ領域のデータにアクセする際に、キャッシュ領域が利用されます。あるスレッドがヒープ領域のデータを更新しても、実際にはキャッシュ領域を更新しているだけで、他のスレッドからは更新前の古いデータを参照していることがあります。このキャッシュ領域はあるタイミングでヒープ領域と同期をとり、最新の状態になります。キャッシュ領域があるため、スレッド間で共有されているデータは安全性を保証することができません。

スレッドセーフでないActionをスレッドセーフにするには、ThreadLocalクラスを利用します。ThreadLocalクラスはスレッド毎に異なるデータを保持します。あるスレッドが保持しているデータに、他のスレッドがアクセスすることはないので、スレッドセーフにすることができます。

ActionFormContextクラスはスレッド毎にActionFormオブジェクトを保持します。

```java
public class ActionFormContext {

    private static ThreadLocal<ActionForm> actionFormThreadLocal = new ThreadLocal<>();

    public static ActionForm getActionFrom() {
        return actionFormThreadLocal.get();
    }

    public static void setActionFrom(ActionForm actionForm) {
        actionFormThreadLocal.set(actionForm);
    }

    public static void removeActionFrom() {
        actionFormThreadLocal.remove();
    }
}
```

CustomActionクラスは、ActionFormContextクラスにActionFormオブジェクトを格納し、ActionFormContext#removeActionFormメソッドが呼び出されるまで、ActionFormオブジェクトを保持します。

```java
public class CustomAction extends Action {

    @Override
    public void execute(ActionForm safeActionForm) {
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
```

ThreadSafeActionクラスは、ActionFormContextクラスからActionFormオブジェクトを取得します。ActionFormContextクラスはスレッド毎に異なるActionFormオブジェクトを返します。

```java
public class ThreadSafeAction extends CustomAction {

    public static final String MESSAGE = "メッセージを上書しました";

    @Override
    public void executeLogic() {
        ActionForm safeActionForm = ActionFormContext.getActionFrom();
        safeActionForm.setMessage(MESSAGE);

        String message = safeActionForm.getMessage();
        if (!MESSAGE.equals(message)) {
            long id = Thread.currentThread().getId();
            System.out.println(String.format(
                    "エラー: %4d, %s, %s, 上書きに失敗しました",
                    id, message, MESSAGE
            ));
        }
    }
}
```

最後にMainクラスのThreadUnsafeActionクラスをThreadSafeクラスに置き換えて、Main#mainを実行するとコンソールにエラーが出力されなくなります。

```
ThreadSafeAction start.
ThreadSafeAction end.
```

## まとめ

複数のスレッドで共有されているオブジェクトのインスタンス変数は、スレッド毎に同時並行的にアクセスされるので、意図しない動作をすることがわかりました。このことにより、スレッド間で共有されたオブジェクトのインスタンス変数は、スレッドセーフでないことがわかりました。インスタンス変数の代わりにThreadLocalを利用して、スレッド毎にオブジェクトを管理すれば、スレッドセーフを実現できることがわかりました。
