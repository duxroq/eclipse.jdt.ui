package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockAssertStatement {

	private AtomicInteger i = new AtomicInteger();
	
	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			assert (i.get() != 4);
		}
	}
}
