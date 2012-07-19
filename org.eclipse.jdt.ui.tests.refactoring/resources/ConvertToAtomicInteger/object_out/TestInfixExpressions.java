package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestInfixExpressions {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(i.get() + i.get());
	}
}
