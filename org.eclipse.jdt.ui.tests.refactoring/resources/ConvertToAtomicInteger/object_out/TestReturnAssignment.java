package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment {
	public AtomicInteger i = new AtomicInteger(12);
	
	public int getI() {
		i.set(12);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}