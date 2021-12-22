package tests;

import zelva.utils.concurrent.LoopRunner;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        LoopRunner runner = new LoopRunner(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("dasd");
        });
        new Thread(runner).start();
        Thread.sleep(500);
        runner.stop();
    }
}
