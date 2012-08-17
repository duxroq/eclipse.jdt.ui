package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundAdditionAssignment_extendedInfixWithMultipleFieldRefs {

	private AtomicInteger f = new AtomicInteger();
	
	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(f.get() + (f.get()*3) + f.get());
	}
}