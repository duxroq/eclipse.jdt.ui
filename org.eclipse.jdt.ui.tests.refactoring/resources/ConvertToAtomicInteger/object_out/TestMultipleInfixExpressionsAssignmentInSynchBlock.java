package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchBlock {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	public void foo() {
		synchronized (this) {
			f.set(12 + a + b);			
		}
	}
}
