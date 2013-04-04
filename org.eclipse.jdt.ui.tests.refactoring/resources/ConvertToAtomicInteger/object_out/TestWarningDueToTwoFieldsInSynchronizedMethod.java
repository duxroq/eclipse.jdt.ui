package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToTwoFieldsInSynchronizedMethod {

	AtomicInteger f = new AtomicInteger();
	int g;

	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	synchronized void twoFieldsInSyncMethod() {
		f.addAndGet(12);
		g = g + 3;
	}
}