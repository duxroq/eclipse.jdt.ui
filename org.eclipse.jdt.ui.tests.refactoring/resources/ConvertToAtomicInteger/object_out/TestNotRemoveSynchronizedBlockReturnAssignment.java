package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockReturnAssignment {

	public AtomicInteger i = new AtomicInteger();

	public int getI() {
		// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
		synchronized (this) {
			i.set(12);
			// TODO Return statements with assignments cannot be refactored into atomic operations.
			return i.get();
		}
	}
}
