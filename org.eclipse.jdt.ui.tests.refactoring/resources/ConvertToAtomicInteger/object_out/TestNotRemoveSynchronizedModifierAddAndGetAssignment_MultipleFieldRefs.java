package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedModifierAddAndGetAssignment_MultipleFieldRefs {

	private AtomicInteger counter = new AtomicInteger();

	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	private synchronized void doubleCounter() {
		// TODO The operations below cannot be executed atomically.
		counter.addAndGet(counter.get());
	}
}
