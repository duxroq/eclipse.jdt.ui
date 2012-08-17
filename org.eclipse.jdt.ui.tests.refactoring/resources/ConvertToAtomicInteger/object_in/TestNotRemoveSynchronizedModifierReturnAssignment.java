package object_in;

public class TestNotRemoveSynchronizedModifierReturnAssignment {

	public int i;

	public boolean fillerMethod() {
		return false;
	}
	
	synchronized public int getI() {
		return i= 12;
	}
}
