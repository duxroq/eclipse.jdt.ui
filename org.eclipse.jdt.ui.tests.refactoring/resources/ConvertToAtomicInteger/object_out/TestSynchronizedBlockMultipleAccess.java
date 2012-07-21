package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedBlockMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	void syncMultipleAccess() {
		synchronized (this) {
			// TODO The statements below are not properly synchronized.
			f.set(12);
			f.getAndIncrement();
		}
	}
}