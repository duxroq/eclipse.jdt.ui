package object_in;

public class TestCompoundAdditionAssignment_infixExpression {

	private int f;
	
	public synchronized void foo() {
		f += 12 + Integer.bitCount(f);
	}
}