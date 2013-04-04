package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReplaceIfStatementWithCompareAndSet_extendedCompareExpression {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		if (i.get() == (Integer.bitCount(i.get()) + 3)) {
			i.set(2);
		}
	}
}