package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchBlock {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	public void foo() {
		synchronized (this) {
			i.set(f++);
		}
	}
	
}
