package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundAdditionAssignment_infixExpression {

	private AtomicInteger f = new AtomicInteger();
	
	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(12 + Integer.bitCount(f.get()));
	}
}