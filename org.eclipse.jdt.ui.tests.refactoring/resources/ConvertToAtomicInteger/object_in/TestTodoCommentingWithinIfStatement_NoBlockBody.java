package object_in;

public class TestTodoCommentingWithinIfStatement_NoBlockBody {

	private int i;

	public void foo() {
		if (i < 3)
			i = i + 12 + (i*4);
	}
}