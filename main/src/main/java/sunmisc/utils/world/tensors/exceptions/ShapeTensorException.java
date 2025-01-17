package sunmisc.utils.world.tensors.exceptions;

import java.util.Arrays;

public final class ShapeTensorException extends RuntimeException {

    public ShapeTensorException(final int[] shape) {
        super(String.format(
                "This tensor has a different shape: %s",
                Arrays.toString(shape))
        );
    }
}
