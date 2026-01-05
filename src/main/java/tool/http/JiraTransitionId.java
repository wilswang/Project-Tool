package tool.http;

/**
 * Jira Transition ID 列舉
 * 定義常用的狀態轉換 ID
 */
public enum JiraTransitionId {
	OPEN(41, "Not Start Yet"),
	REJECT_1(301, "IN ANALYSIS"),
	TO_DEV(101, "IN DEV"),
	REJECT(111, "Ready to DEV"),
	DEV_DONE(121, "DEV DONE"),
	RESOLVED(221, "Resolved");

	private final int id;
	private final String result;

	JiraTransitionId(int id, String result) {
		this.id = id;
		this.result = result;
	}

	public int getId() {
		return id;
	}

	public String getResult() {
		return result;
	}

	public String getIdAsString() {
		return String.valueOf(id);
	}

	@Override
	public String toString() {
		return result + " (" + id + ")";
	}
}
