package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	void syncMultipleAccess() {
		synchronized(this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			f.getAndIncrement();
			f.getAndIncrement();
			f.getAndIncrement();
		}
	}
}