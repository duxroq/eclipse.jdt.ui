package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToMultiplication {

	AtomicInteger f = new AtomicInteger();
	int j;

	void multiply() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() * 12);
	}
}