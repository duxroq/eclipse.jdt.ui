package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToFieldAccessedTwiceInSynchronizedBlock {

	AtomicInteger f = new AtomicInteger();

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			f.addAndGet(12);
			f.getAndIncrement();
		}
	}
}