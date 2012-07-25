package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAddAssign {

	AtomicInteger f = new AtomicInteger();

	public void bar() {
		f.addAndGet(12);
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void fooFoo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(12 + bar());
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void fooFoo2() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(f.get() + (f.get()*3) + f.get());
	}

	// TODO The statements in the method below are not properly synchronized.

	public synchronized void fooFoo3() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet((f.get()-bar()));
	}
}
