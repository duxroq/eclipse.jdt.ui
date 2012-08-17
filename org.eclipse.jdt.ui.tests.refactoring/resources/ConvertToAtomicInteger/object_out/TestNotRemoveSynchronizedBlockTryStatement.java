package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestNotRemoveSynchronizedBlockTryStatement {

	private AtomicInteger i = new AtomicInteger();

	public void bar() {
		synchronized (this) {
			// TODO This block is not properly synchronized in relation to other accesses to the refactored field.
			try {
				exceptionMethod();
			} catch (Exception e) {
				i.getAndDecrement();
			} finally {
				// TODO The operations below cannot be executed atomically.
				i.set(i.get() * 2);
			}
		}
	}
	
	public void exceptionMethod() throws Exception {
		throw new Exception("Error");
	}
}
