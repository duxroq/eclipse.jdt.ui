package object_in;

public class TestNotRemoveSynchronizedModifierSetAndGetAssignment {

	int counter;

	private synchronized void bar() {
		counter = (counter*3) - counter;
	}
}
