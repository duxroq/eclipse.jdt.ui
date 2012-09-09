package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReplacehIfStatementWithCompareAndSet_extendedSetExpression {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void foo() {
		i.compareAndSet(j, j + 12 + (i.get()*2));
	}
}