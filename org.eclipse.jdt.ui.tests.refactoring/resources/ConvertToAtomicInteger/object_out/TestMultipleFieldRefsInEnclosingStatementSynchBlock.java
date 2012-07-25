package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchBlock {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	public void foo() {
		synchronized (this) {
			// TODO The operations below cannot be executed atomically.
			// TODO The statements in this block are not properly synchronized.
			i.set(f++);
		}
	}
}
