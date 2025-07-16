import tool.JsonToFile2;
import tool.UrlChecker;

public class MainSelector {
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("請指定要執行的工具，例如: java MainSelector A");
			return;
		}
		
		String option = args[0];
		
		switch (option.toUpperCase()) {
			case "A":
				JsonToFile2.main(new String[]{}); // 可傳遞額外參數
				break;
			case "B":
				UrlChecker.main(new String[]{});
				break;
			default:
				System.out.println("未知選項: " + option);
				System.out.println("使用方式: java MainSelector [A|B]");
				break;
		}
	}
}