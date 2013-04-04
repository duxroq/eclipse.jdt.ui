package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockForWhileStatement {

	private AtomicInteger i = new AtomicInteger(0);
	
	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			while (i.get() < 10) {
				i.addAndGet(2);
			}
		}
	}
}