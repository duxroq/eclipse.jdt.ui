package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSetter {
	
	AtomicInteger value = new AtomicInteger(0);

    public void setCounter(int counter) {
        this.value.set(counter);
    }
}