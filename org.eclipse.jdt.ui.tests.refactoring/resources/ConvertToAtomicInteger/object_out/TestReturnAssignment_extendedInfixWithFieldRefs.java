package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment_extendedInfixWithFieldRefs {

	private AtomicInteger i = new AtomicInteger();

	public int getI() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get() + i.get());
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}