package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedMethodMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	// TODO The statements in the method below are not properly synchronized.

	synchronized void syncMultipleAccess() {
		f.set(12);
		f.getAndIncrement();
	}
}