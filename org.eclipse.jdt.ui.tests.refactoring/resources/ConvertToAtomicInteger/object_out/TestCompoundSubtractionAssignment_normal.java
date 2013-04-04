package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundSubtractionAssignment_normal {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		i.addAndGet(-12);
	}
}