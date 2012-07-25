package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment {
	public AtomicInteger i = new AtomicInteger(12);
	
	public int getI() {
		i.set(12);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
	
	public int getI2() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get() + i.get() + 12);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
	
	public int getI3() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * 2);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
	
	public int getI4() {
		return i.get();
	}
	
	// TODO The statements in the method below are not properly synchronized.

	public synchronized int getI5() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get());
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
	
	// TODO The statements in the method below are not properly synchronized.

	public synchronized int getI6() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get());
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}