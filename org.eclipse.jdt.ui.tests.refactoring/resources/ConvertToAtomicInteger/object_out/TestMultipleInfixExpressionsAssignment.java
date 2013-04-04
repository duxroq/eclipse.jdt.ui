package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsAssignment {

	AtomicInteger f = new AtomicInteger();
	int a;
	int b;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		f.set(12 + a + b);
	}
}
