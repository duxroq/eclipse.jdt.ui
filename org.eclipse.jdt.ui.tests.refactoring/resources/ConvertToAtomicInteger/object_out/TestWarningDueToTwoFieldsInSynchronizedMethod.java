package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToTwoFieldsInSynchronizedMethod {

	AtomicInteger f = new AtomicInteger();
	int g;

	synchronized void twoFieldsInSyncMethod() {
		// TODO The statements below are not properly synchronized.
		f.addAndGet(12);
		g = g + 3;
	}
}
