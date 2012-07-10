package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAddAssign {

	AtomicInteger f = new AtomicInteger();
	
	public void bar() {
		f.addAndGet(12);
	}
}
