package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestPrimitiveTypeCastConversions_long {

	private AtomicInteger i = new AtomicInteger();

	public long getLong() {
		return i.longValue();
	}
}