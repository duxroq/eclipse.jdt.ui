package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestSubtraction {

	AtomicInteger f = new AtomicInteger();
	int j;

	void subtract() {
		f.addAndGet(-12);
	}
}