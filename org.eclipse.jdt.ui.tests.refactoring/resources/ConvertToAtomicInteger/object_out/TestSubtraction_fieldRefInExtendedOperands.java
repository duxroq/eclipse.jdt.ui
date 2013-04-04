package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtraction_fieldRefInExtendedOperands {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(j - 12 - i.get());
	}
}