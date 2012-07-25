package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedBlockMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	void syncMultipleAccess() {
		synchronized (this) {
			// TODO The statements in this block are not properly synchronized.
			f.set(12);
			f.getAndIncrement();
		}
	}
}