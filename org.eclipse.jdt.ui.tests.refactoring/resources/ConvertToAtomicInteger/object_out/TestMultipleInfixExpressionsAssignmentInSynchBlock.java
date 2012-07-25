package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchBlock {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	public void foo() {
		synchronized (this) {
			// TODO The operations below cannot be executed atomically.
			// TODO The statements in this block are not properly synchronized.
			f.set(12 + a + b);			
		}
	}
}
