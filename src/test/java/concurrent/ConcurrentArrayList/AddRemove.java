package concurrent.ConcurrentArrayList;

import zelva.utils.concurrent.ConcurrentArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AddRemove {
    private static final ConcurrentArrayList<Integer> target
            = new ConcurrentArrayList<>();
    private static final List<Integer> valid
            = new ArrayList<>() {
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Integer i : this) {
                builder.append(i).append(' ');
            }
            return builder.toString();
        }
    };


    public static void main(String[] args) {

        for (int i = 0; i < 1000; ++i) {
            add(i);
        }
        for (int i = 64; i < 129; ++i) {
            int t = ThreadLocalRandom.current().nextInt(0, 200);
            remove(t);
        }
        add(-123);
        String r1 = valid.toString(), r2 = target.toString();
        System.out.println("Passed "+r1.equals(r2));
        System.out.println("Valid result:\n"+r1);
        System.out.println("Target result:\n"+r2);
    }


    private static void add(Integer e) {
        target.add(e); valid.add(e);
    }
    private static void remove(int index) {
        target.remove(index); valid.remove(index);
    }
}
