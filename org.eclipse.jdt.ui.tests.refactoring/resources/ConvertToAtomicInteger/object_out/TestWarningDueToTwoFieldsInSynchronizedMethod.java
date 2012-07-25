package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToTwoFieldsInSynchronizedMethod {

	AtomicInteger f = new AtomicInteger();
	int g;

	// TODO The statements in the method below are not properly synchronized.

	synchronized void twoFieldsInSyncMethod() {
		f.addAndGet(12);
		g = g + 3;
	}
}
