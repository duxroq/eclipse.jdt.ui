package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment {
	public AtomicInteger i = new AtomicInteger(12);
	
	public int getI() {
		i.set(12);
		// TODO The return assignment could not be executed atomically.
		return i.get();
	}
	
	public int getI2() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get() + i.get() + 12);
		// TODO The return assignment could not be executed atomically.
		return i.get();
	}
	
	public int getI3() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * 2);
		// TODO The return assignment could not be executed atomically.
		return i.get();
	}
}