package object_in;

public class TestReplaceIfStatementWithCompareAndSet_extendedCompareExpression {

	private int i;
	
	public void foo() {
		if (i == (Integer.bitCount(i) + 3)) {
			i=2;
		}
	}
}