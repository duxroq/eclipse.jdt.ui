package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestInfixExpressions5 {

	private AtomicInteger i = new AtomicInteger();
	int j;
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get() * 12 * (i.get()/2) * j);
	}
}
