package concurrent.ConcurrentArrayMap;

import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.utils.concurrent.ConcurrentArrayMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConcurrentStressArrayMap {

    public static void main(String[] args) throws Exception {Main.main(args);}

    @JCStressTest
    @Outcome(
            id = {
                    "x",
            },
            expect = Expect.ACCEPTABLE,
            desc = "all updates"
    )
    @State
    public static class CombineArrayIndex extends ConcurrentArrayMap<List<String>> {
        public CombineArrayIndex() { super(2); }
        @Actor
        public void actor1() {
            List<String> list = computeIfAbsent(0,
                    k -> Collections.synchronizedList(new ArrayList<>()));
            list.add("Bar");
            resize(x -> 5);
        }

        @Actor
        public void actor2() {
            List<String> list = computeIfAbsent(1,
                    k -> Collections.synchronizedList(new ArrayList<>()));
            list.add("Baz");
            resize(x -> 4);
        }

        @Arbiter
        public void arbiter(L_Result s) {
            s.r1 = super.toString();
        }
    }
}