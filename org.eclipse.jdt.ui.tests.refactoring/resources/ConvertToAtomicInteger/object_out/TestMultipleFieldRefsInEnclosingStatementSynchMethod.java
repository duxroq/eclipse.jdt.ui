package object_in;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchMethod {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	// TODO The statements in the method below are not properly synchronized.

	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		i.set(f++);
	}
}