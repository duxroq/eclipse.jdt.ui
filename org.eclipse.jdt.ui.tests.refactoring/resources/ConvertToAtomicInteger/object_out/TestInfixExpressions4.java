package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestInfixExpressions4 {

	private AtomicInteger i = new AtomicInteger();
	int j;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(j + 12 + j + j);
	}
}
