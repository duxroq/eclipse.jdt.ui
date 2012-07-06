package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedBlock {

	AtomicInteger f = new AtomicInteger();

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			// TODO The statement below is not properly synchronized.
			f.addAndGet(12);
			// TODO The statement below is not properly synchronized.
			f.getAndIncrement();
		}
	}
}
