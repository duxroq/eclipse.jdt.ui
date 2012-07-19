package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpressionsWithReferenceToChosenField {

	AtomicInteger f = new AtomicInteger();
	int a;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(12 + a);
	}
}