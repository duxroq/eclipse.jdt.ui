package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAddition {

	AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		i.addAndGet(12);
	}
}