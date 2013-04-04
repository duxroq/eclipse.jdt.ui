package object_in;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchMethod {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	// TODO The method below is not properly synchronized in relation to other accesses to the refactored field.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(f++);
	}
}