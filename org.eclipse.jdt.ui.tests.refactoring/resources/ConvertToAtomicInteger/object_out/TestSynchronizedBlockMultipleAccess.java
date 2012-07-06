package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedBlockMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	void syncMultipleAccess() {
		synchronized (this) {
			// TODO The statement below is not properly synchronized.
			f.set(12);
			// TODO The statement below is not properly synchronized.
			f.getAndIncrement();
		}
	}
}