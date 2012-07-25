package invalid;

public class TestSideEffectsOnIntFieldInAssignment {

	private int i;
	
	public void foo() {
		i = i++;
	}
}
