package object_in;

public class TestCompoundAdditionAssignment_extendedInfixWithMultipleFieldRefs {

	private int f;
	
	public synchronized void foo() {
		f += f + (f*3) + f;
	}
}