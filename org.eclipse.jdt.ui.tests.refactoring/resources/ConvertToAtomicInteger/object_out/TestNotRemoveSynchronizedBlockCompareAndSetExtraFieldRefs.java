package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockCompareAndSetExtraFieldRefs {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void foo() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			// TODO The operations below cannot be executed atomically.
			i.compareAndSet(j, ++j);
		}
	}
}