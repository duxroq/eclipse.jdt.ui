package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignmentInSynchMethod {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	public synchronized void foo() {
		f.set(b + 12 + a);
	}
}