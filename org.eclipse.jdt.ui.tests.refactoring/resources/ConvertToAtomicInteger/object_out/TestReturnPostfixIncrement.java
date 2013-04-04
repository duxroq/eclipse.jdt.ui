package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnPostfixIncrement {

	AtomicInteger value = new AtomicInteger(0);
	
	public int inc() {
        return value.getAndIncrement();
    }
}
