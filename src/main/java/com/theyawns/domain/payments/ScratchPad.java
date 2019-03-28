package com.theyawns.domain.payments;

import java.util.Arrays;
import java.util.List;

public class ScratchPad {

    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5);

    private void useForLoop() {
        for (int i : input)
            longRunningOperation(i);
    }

    private void useStream() {
        input.stream().forEach((i) -> longRunningOperation(i));
    }

    private void useParallelStream() {
        input.parallelStream().forEach((i) -> longRunningOperation(i));
    }


    public static void main(String[] args) {
        ScratchPad main = new ScratchPad();
        long start = System.nanoTime();
        main.useForLoop();
        long middle = System.nanoTime();
        main.useStream();
        long mid2 = System.nanoTime();
        main.useParallelStream();
        long end = System.nanoTime();

        long elapsed1 = middle - start;
        long elapsed2 = mid2 - middle;
        long elapsed3 = end - mid2;
        System.out.println(elapsed1);
        System.out.println(elapsed2);
        System.out.println(elapsed3);


    }

    private void longRunningOperation(int i) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
