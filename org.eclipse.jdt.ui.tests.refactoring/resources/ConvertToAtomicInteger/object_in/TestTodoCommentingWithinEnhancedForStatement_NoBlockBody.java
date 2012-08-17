package object_in;

public class TestTodoCommentingWithinEnhancedForStatement_NoBlockBody {
	
	private int i;
	
	public void foo() {
		for (int a : list)
			i= i + 12 + list.get(0);
	}
}
