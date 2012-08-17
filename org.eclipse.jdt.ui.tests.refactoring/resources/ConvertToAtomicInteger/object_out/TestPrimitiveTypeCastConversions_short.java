package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestPrimitiveTypeCastConversions_short {

	private AtomicInteger i = new AtomicInteger();

	public short getShort() {
		return i.shortValue();
	}
}