package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedMethod {

	AtomicInteger f = new AtomicInteger();

	// TODO The statements in the method below are not properly synchronized.

	synchronized void twoFieldsInSyncMethod() {
		f.addAndGet(-12);
		f.getAndIncrement();
	}
}
