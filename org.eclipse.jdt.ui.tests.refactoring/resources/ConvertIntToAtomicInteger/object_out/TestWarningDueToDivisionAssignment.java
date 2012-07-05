package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToDivisionAssignment {

	AtomicInteger f = new AtomicInteger();

	void divide() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() / 12);
	}
}
