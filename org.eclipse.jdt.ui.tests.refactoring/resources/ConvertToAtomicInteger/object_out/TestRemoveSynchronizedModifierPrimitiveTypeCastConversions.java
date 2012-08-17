package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestRemoveSynchronizedModifierPrimitiveTypeCastConversions {

	private AtomicInteger i = new AtomicInteger();

	public double foo() {
		return i.doubleValue();
	}
}