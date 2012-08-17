package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestPrimitiveTypeCastConversions_double {

	private AtomicInteger i = new AtomicInteger();

	public double getDouble() {
		return i.doubleValue();
	}
}