package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestInfixExpressionWithMixedOperands {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() + 5 - j * 2 + 3 / 2);
	}
}