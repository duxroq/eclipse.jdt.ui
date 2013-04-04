package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment_compoundTimesOperator {

	private AtomicInteger i = new AtomicInteger();

	public int foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * 2);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}