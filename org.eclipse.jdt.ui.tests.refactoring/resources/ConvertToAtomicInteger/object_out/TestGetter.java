package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public static class TestGetter {
    AtomicInteger value = new AtomicInteger(0);
    
    public int getCounter() {
        return value.get();
    }
}