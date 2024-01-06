package sunmisc.utils.math;

@FunctionalInterface
public interface ProgressionFunction {

    Progression of(int i);


    interface Progression {

        Number sum();

        Number value();

    }
}
