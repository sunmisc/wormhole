package sunmisc.utils.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * This is a helper class for {@link CompletableFuture} that
 * provides the desired order of execution the action chain in a common scope
 *
 * @author Sunmisc Unsafe
 */

@SuppressWarnings("forRemoval")
public class SequentialScope<U> {
    private volatile CompletableFuture<U> stack = completedFuture(null);

    public CompletableFuture<U> runOrSchedule(
            Function<U, ? extends CompletionStage<U>> function) {
        var d = new CompletableFuture<U>();

        @SuppressWarnings("unchecked")
        var oldState = (CompletableFuture<U>) STACK.getAndSet(this, d);

        oldState.thenCompose(function)
                .whenComplete((r, t) -> complete(d,r,t));
        return d;
    }


    public void cancel() {
        stack = CompletableFuture.failedFuture(new CancellationException());
    }

    private static <U> void
    complete(CompletableFuture<U> cf,
             U result,
             Throwable t) {
        if (t == null)
            cf.complete(result);
        else
            cf.completeExceptionally(t);
    }

    private static final VarHandle STACK;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STACK = l.findVarHandle(SequentialScope.class,
                    "stack", CompletableFuture.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
