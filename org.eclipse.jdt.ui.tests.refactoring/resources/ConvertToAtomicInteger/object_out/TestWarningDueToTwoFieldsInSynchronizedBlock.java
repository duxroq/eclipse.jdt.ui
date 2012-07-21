package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToTwoFieldsInSynchronizedBlock {

	AtomicInteger f = new AtomicInteger();
	int g;

	void twoFieldsInSyncBlock() {
		synchronized (this) {
			// TODO The statements below are not properly synchronized.
			f.addAndGet(12);
			g = g + 3;
		}
	}
}
