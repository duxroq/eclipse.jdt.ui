package object_in;

public class TestTodoCommentingWithinSwitchCases {

	private int i;
	
	public void foo() {
		switch (i) {
			case 4:
				i = i * 2;
				break;
			case 6:
				i++;
				break;
			default:
				break;
		}
	}
}
