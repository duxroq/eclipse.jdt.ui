package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignmentInSynchronizedBlock {

	public AtomicInteger i = new AtomicInteger();

	public int getI() {
		// TODO The statements in this block are not properly synchronized.
		synchronized (this) {
			i.set(12);
			// TODO Return statements with assignments cannot be refactored into atomic operations.
			return i.get();
		}
	}
}
