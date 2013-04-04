package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestRemoveSynchronizedBlockAddAndGetAssignment {

	AtomicInteger counter = new AtomicInteger();
	
	private void subtract23() {
		counter.addAndGet(-23);
	}
}
