package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestInfixExpressions2 {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet((i.get()*2) + i.get() + (i.get()*3));
	}
}
