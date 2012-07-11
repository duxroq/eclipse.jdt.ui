package object_in;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultipleFieldRefsInEnclosingStatementSynchMethod {

	AtomicInteger i = new AtomicInteger();
	int f;
	
	public synchronized void foo() {
		i.set(f++);
	}
}