package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockForForStatement {

	private AtomicInteger i = new AtomicInteger();

	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			for (i.set(0); i.get()<10; i.getAndIncrement()) {
				i.addAndGet(2);
			}
		}
	}
}