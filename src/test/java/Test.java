import sunmisc.utils.concurrent.ConcurrentEnumMap;

import java.util.concurrent.TimeUnit;

public class Test {


    public static void main(String[] args) {
        ConcurrentEnumMap<TimeUnit, String> map
                = new ConcurrentEnumMap<>(TimeUnit.class);

        map.put(TimeUnit.MILLISECONDS, "ms");

        map.put(TimeUnit.SECONDS, "s");


        map.put(TimeUnit.MINUTES, "m");

        System.out.println(map);

        System.out.println(map.values().contains("s"));

    }
}
