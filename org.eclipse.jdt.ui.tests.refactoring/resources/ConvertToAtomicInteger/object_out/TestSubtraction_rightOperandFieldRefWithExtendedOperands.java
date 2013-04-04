package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtraction_rightOperandFieldRefWithExtendedOperands {

	private AtomicInteger i = new AtomicInteger();
	private int j;

	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(12 - i.get() - j - 3);
	}
}