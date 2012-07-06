package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedMethod {

	AtomicInteger f = new AtomicInteger();

	synchronized void twoFieldsInSyncMethod() {
		// TODO The statement below is not properly synchronized.
		f.addAndGet(-12);
		// TODO The statement below is not properly synchronized.
		f.getAndIncrement();
	}
}
