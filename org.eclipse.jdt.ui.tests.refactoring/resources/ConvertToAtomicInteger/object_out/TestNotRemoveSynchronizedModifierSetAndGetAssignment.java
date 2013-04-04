package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedModifierSetAndGetAssignment {

	AtomicInteger counter = new AtomicInteger();

	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	private synchronized void bar() {
		// TODO The operations below cannot be executed atomically.
		counter.set((counter.get()*3) - counter.get());
	}
}
