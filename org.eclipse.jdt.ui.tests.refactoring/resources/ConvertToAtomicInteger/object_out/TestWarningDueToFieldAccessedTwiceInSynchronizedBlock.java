package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedBlock {

	AtomicInteger f = new AtomicInteger();

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			// TODO The statements in this block are not properly synchronized.
			f.addAndGet(12);
			f.getAndIncrement();
		}
	}
}
