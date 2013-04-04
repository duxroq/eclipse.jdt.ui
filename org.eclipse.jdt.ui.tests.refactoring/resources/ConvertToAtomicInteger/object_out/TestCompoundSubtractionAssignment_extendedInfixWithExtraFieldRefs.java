package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestCompoundSubtractionAssignment_extendedInfixWithExtraFieldRefs {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		// TODO The operations below cannot be executed atomically.
		i.addAndGet(-((i.get()*3) + i.get() + i.get()*2));
	}
}