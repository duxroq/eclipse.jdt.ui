package invalid;

public class TestPostfixSideEffectsOnIntFieldInAssignment {

	private int i;
	
	public void foo() {
		i = i++;
	}
}
