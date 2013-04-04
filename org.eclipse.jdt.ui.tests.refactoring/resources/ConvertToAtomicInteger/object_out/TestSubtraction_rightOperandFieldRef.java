package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtraction_rightOperandFieldRef {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(12 - i.get());
	}
}