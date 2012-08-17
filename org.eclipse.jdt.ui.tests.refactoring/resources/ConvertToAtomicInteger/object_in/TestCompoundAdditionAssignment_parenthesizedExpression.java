package object_in;

public class TestCompoundAdditionAssignment_parenthesizedExpression {

	private int f;

	public synchronized void fooFoo3() {
		f +=(f-Integer.bitCount(f));
	}
}
