package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpWithMultipleRefsToChosenField {

	AtomicInteger i = new AtomicInteger();
	int j;

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(j + 12 + i.get());
	}
}