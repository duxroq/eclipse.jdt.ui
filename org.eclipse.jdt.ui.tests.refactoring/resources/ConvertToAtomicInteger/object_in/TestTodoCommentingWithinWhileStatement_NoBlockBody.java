package object_in;

public class TestTodoCommentingWithinWhileStatement_NoBlockBody {

	private int i;

	public void foo() {
		while (i < 10)
			i =i + 12 + (i*2);
	}
}