package object_in;

public class TestTodoCommentingWithinIfStatement_elseStatement {

	private int i;

	public void foo() {
		if (i==3)
			i=2 + i + foo();
		else {
			i++;
			i*=2;
		}
	}
}