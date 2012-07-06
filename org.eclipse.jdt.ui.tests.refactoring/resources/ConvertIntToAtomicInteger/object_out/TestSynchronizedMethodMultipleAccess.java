package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedMethodMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	synchronized void syncMultipleAccess() {
		// TODO The statement below is not properly synchronized.
		f.set(12);
		// TODO The statement below is not properly synchronized.
		f.getAndIncrement();
	}
}