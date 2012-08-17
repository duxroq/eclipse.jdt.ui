package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestMultiplication_extendedOperandsWithFieldRefs {

	private AtomicInteger i = new AtomicInteger();
	private int j;
	
	public void ioo() {
		// TODO The operations below cannot be executed atomically.
		i.set(i.get()*(i.get()/3)*j*2*i.get());
	}
}