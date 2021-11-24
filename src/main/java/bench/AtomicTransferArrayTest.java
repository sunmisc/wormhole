package bench;

import com.sun.jna.WString;
import org.openjdk.jcstress.Main;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.L_Result;
import zelva.concurrent.AtomicTransferArray;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public class AtomicTransferArrayTest {

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }

    public static class AtomicTrasformerArray extends AtomicTransferArray<Integer> {

        public Node<Integer>[] set(int i) {
            return set(i, i);
        }
        public String getResult() {
            return super.toString();
        }
    }
    @JCStressTest
   // @Outcome(id = "[0, 1, 2, 3]", expect = ACCEPTABLE, desc = "Boring")
    @State
    public static class JcstressTest extends AtomicTrasformerArray {
        @Actor
        public void actor1() {
            Node<Integer>[] arr = array;
            int len = arr.length;
            transfer(arr, new AtomicTransferArray.Node[len = len << 1]);
            set(0);
            transfer(array, new AtomicTransferArray.Node[len << 1]);
            set(2);
        }

        @Actor
        public void actor2() {
            Node<Integer>[] arr = array;
            int len = arr.length;
            transfer(arr, new AtomicTransferArray.Node[len = len << 1]);
            set(1);
            transfer(array, new AtomicTransferArray.Node[len << 1]);
            set(3);
        }
        @Arbiter
        public void result(L_Result l) {
            l.r1 = getResult();
        }
    }
}
