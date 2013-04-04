package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAssignmentInForLoop {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		for (i.set(0); i.get()/2 < 10; i.set(i.get()*2)) {
			// TODO The operations below cannot be executed atomically.
			i.set(i.get()*2);
		}
	}
}
