package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReplaceIfStatementWithCompareAndSet {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		i.compareAndSet(3, 2);
	}
}