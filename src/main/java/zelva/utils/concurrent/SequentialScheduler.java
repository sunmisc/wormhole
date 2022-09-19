package zelva.utils.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * @author ZelvaLea
 */
public class SequentialScheduler {
    final AtomicReference<CompletableFuture<?>> stack
            = new AtomicReference<>(completedFuture(null));

    public <U> CompletableFuture<U> runOrSchedule(
            Supplier<? extends CompletionStage<U>> supplier
    ) {
        CompletableFuture<U> d
                = new CompletableFuture<>();
        stack.getAndSet(d)
                .thenCompose(x -> supplier.get())
                .whenComplete((r,t) -> complete(d,r,t));
        return d;
    }


    public void cancel() {
        stack.get().obtrudeException(
                new CancellationException()
        );
    }

    private static <U> void complete(
            CompletableFuture<U> cf,
            U result,
            Throwable t) {
        if (t == null) {
            cf.complete(result);
        } else {
            cf.completeExceptionally(t);
        }
    }
}
