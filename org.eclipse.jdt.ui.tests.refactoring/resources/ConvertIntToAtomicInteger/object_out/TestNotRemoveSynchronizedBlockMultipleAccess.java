package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	void syncMultipleAccess() {
		synchronized(this) {
			// TODO The statement below is not properly synchronized.
			f.getAndIncrement();
			// TODO The statement below is not properly synchronized.
			f.getAndIncrement();
			// TODO The statement below is not properly synchronized.
			f.getAndIncrement();
		}
	}
}