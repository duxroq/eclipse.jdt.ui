package invalid;

public class TestAssignmentSideEffectsWithinAssignment {

	private int i;
	private int a;
	
	public void foo() {
		i = i + (i=2);
	}
}
