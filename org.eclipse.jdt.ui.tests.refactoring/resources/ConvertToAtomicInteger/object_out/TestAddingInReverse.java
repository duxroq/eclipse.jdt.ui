package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAddingInReverse {

	AtomicInteger i = new AtomicInteger();
	
	public void addReverse() {
		i.addAndGet(12);
	}
}