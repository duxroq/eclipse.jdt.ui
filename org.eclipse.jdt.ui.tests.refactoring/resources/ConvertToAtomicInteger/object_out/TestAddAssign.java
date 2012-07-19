package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestAddAssign {

	AtomicInteger f = new AtomicInteger();
	
	public void bar() {
		f.addAndGet(12);
	}
	
	public synchronized void fooFoo() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(12 + bar());
	}
	
	public synchronized void fooFoo2() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet(f.get() + (f.get()*3) + f.get());
	}
	
	public synchronized void fooFoo3() {
		// TODO The operations below cannot be executed atomically.
		f.addAndGet((f.get()-bar()));
	}
}
