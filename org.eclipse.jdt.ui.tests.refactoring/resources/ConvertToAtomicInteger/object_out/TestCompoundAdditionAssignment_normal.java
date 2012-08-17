package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundAdditionAssignment_normal {

	AtomicInteger f = new AtomicInteger();

	public void foo() {
		f.addAndGet(12);
	}
}
