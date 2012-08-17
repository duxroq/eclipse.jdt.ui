package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultiplication_extendedOperands {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(j * bar() * 2);
	}
}