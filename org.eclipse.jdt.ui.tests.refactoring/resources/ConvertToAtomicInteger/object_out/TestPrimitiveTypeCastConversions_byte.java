package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestPrimitiveTypeCastConversions_byte {

	private AtomicInteger i = new AtomicInteger();

	public byte getByte() {
		return i.byteValue();
	}
}