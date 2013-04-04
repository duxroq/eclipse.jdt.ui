package object_in;

public class TestReplaceIfStatementWithCompareAndSet_extendedSetExpression {

	private int i;
	private int j;
	
	public void foo() {
		if (i == j) {
			i= j + 12 + (i*2);
		}
	}
}