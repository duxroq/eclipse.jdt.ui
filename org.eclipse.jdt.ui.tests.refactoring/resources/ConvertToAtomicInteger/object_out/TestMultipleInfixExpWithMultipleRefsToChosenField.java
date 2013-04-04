package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleInfixExpWithMultipleRefsToChosenField {

	AtomicInteger i = new AtomicInteger();
	int j;

	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(j + 12 + i.get());
	}
}