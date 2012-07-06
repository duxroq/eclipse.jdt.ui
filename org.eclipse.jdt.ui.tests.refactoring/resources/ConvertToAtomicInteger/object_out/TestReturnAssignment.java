package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignment {
	public AtomicInteger i = new AtomicInteger(12);
	
	public int getI() {
		i.set(12);
		return i.get();
	}
}