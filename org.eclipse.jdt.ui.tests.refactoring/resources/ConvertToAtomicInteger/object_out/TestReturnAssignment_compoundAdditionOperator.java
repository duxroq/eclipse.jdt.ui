package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment_compoundAdditionOperator {

	private AtomicInteger i = new AtomicInteger();
	
	public int foo() {
		i.addAndGet(2);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}