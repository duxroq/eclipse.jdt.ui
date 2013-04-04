package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundMultiplicationAssignment {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * (i.get() + Integer.bitCount(i.get()) * (i.get()/3) - 12));
	}
}