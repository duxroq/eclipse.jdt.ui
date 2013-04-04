package object_in;

public class TestNotRemoveSynchronizedModifierAddAndGetAssignment_MultipleFieldRefs {

	private int counter;

	private synchronized void doubleCounter() {
		counter = counter + counter;
	}
}
