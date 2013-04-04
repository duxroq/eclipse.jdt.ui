package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchBlock {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	public void foo() {
		synchronized (this) {
			// TODO The operations below cannot be executed atomically.
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			f.set(12 + a + b);			
		}
	}
}
