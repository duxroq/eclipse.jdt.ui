package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtraction_leftOperandFieldRefWithExtendedOperands {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(-(i.get() + j + (i.get()*3)));
	}
}