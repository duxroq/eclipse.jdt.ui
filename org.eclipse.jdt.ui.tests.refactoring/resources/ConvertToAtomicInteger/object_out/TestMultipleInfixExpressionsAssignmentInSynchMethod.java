package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchMethod {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.set(b + 12 + a);
	}
}