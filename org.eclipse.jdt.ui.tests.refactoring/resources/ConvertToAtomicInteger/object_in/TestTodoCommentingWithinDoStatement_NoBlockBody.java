package object_in;

public class TestTodoCommentingWithinDoStatement_NoBlockBody {

	private int i;

	public void foo() {
		do 
			i= i + i + 12;
		while (i < 3);
	}
}