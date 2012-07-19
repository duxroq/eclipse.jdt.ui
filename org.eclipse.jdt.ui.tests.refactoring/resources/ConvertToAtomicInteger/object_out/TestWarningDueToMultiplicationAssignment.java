package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestWarningDueToMultiplicationAssignment {

	AtomicInteger f = new AtomicInteger();
	int j;

	void multiply() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() * 12);
	}
	
	public synchronized void foo() {
		// TODO The operations below cannot be executed atomically.
		f.set(f.get() * ((f.get()-multiply())*f.get()*j));
	}
}
