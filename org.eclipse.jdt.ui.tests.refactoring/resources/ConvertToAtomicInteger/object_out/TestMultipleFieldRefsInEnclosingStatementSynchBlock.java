package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchBlock {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	public void foo() {
		synchronized (this) {
			// TODO The operations below cannot be executed atomically.
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			i.set(f++);
		}
	}
}
