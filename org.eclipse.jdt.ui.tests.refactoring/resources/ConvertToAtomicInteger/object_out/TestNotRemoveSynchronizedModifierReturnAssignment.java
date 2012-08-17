package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedModifierReturnAssignment {

	public AtomicInteger i = new AtomicInteger();

	public boolean fillerMethod() {
		return false;
	}
	
	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	synchronized public int getI() {
		i.set(12);
		// TODO Return statements with assignments cannot be refactored into atomic operations.
		return i.get();
	}
}
