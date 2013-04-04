package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestRemoveSynchronizedModifierIntToDoubleConversion {

	private AtomicInteger i = new AtomicInteger();

	public double foo() {
		return i.doubleValue();
	}	
}