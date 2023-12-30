package sunmisc.utils.concurrent.ConcurrentEnumMap;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public enum TestEnum {
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z;

    static TestEnum rand() {
        Random r = ThreadLocalRandom.current();

        return values[r.nextInt(values.length)];
    }

    private static final Comparator<Server> cmp = new Comparator<Server>() {
        @Override
        public int compare(Server o1, Server o2) {
            int x = o2.priority0(), y = o1.priority0();
            int cmp = Integer.compare(x,y);

            return cmp == 0
                    ? (ThreadLocalRandom.current().nextInt(-1, 1))
                    : cmp;
        }
    };
    public static void main(String[] args) {
        List<Server> list = new ArrayList<>(List.of(
                new Server(ArenaState.STARTING, 10),
                new Server(ArenaState.RUNNABLE, 5),
                new Server(ArenaState.WAITING, 12),
                new Server(ArenaState.WAITING, 0),
                new Server(ArenaState.WAITING, 1),
                new Server(ArenaState.TERMINATED, 10),
                new Server(ArenaState.RESTART, 6)
        ));
        list.sort(cmp);
        StringJoiner joiner = new StringJoiner("\n");
        list.forEach(x -> joiner.add(x.toString()));
        System.out.println(joiner);
    }
    private static record Server(ArenaState state, int online) {


        private int priority() {
            int m = online >= 10 ? 0 : online + 1;
            return m * state.priority;
        }
        private int priority0() {
            int m = online >= 10 ? 0 : 1;
            return m * state.priority;
        }
    }

    public enum ArenaState {
        WAITING(1),
        RUNNABLE(0),
        STARTING(-1),
        RESTART(-1),
        TERMINATED(-1);

        final int priority;

        ArenaState(int priority) {
            this.priority = priority;
        }
    }

    private static final TestEnum[] values = values();

}
