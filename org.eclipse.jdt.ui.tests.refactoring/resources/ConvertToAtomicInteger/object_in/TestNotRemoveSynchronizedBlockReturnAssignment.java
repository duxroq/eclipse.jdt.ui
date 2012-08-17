package object_in;

public class TestNotRemoveSynchronizedBlockReturnAssignment {

	public int i;

	public int getI() {
		synchronized (this) {
			return i= 12;
		}
	}
}
