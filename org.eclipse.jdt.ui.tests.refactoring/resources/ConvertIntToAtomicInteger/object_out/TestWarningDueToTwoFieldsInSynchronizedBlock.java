package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToTwoFieldsInSynchronizedBlock {

	AtomicInteger f = new AtomicInteger();
	int g;

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			// TODO The statement below is not properly synchronized.
			f.addAndGet(12);
			// TODO The statement below is not properly synchronized.
			g = g + 3;
		}
	}
}
