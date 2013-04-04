package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSynchronizedMethodMultipleAccess {

	AtomicInteger f = new AtomicInteger();

	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	synchronized void syncMultipleAccess() {
		f.set(12);
		f.getAndIncrement();
	}
}