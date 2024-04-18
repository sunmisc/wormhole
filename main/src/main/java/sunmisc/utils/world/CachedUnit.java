package sunmisc.utils.world;

public final class CachedUnit implements Unit {

    private final Number[] result;


    public CachedUnit(Unit origin) {
        this.result = origin.values();
    }

    @Override
    public Number[] values() {
        return result;
    }
}
