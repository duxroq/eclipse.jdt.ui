package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchMethod {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	// TODO The statements in the method below are not properly synchronized.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.set(b + 12 + a);
	}
}