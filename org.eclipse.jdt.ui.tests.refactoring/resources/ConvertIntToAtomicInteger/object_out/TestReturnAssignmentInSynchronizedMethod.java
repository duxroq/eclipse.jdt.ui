package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestReturnAssignmentInSynchronizedMethod {

	public AtomicInteger i = new AtomicInteger();

	public boolean fillerMethod() {
		return false;
	}
	
	// TODO The statements in the method below are not properly synchronized.

	synchronized public int getI() {
		i.set(12);
		return i.get();
	}
}
