package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestPrimitiveTypeCastConversions_float {

	private AtomicInteger i = new AtomicInteger();
	
	public float getFloat() {
		return i.floatValue();
	}
}