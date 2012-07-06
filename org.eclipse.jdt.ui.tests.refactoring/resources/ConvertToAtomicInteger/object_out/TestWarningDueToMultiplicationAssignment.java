package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToMultiplicationAssignment {

	AtomicInteger f = new AtomicInteger();

	void multiply() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() * 12);
	}
}
